package com.fpt.printhub_3d.service.impl;
import com.fpt.printhub_3d.dto.payment.*;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.PaymentService;
import com.fpt.printhub_3d.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import vn.payos.PayOS;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class PaymentServiceImpl implements PaymentService {
    private final OrderRepository orders;
    private final CustomOrderRepository customs;
    private final PaymentRepository payments;
    private final PayOS payOS;
    private final NotificationService notifications;
    @Value("${payos.return-url:http://localhost:5173/payment-result}") private String returnUrl;
    @Value("${payos.cancel-url:http://localhost:5173/payment-result?cancel=true}") private String cancelUrl;

    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private Payment lockPayment(String code) {
        var p=payments.findByTransactionId(code).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy giao dịch"));
        if(p.getOrder()!=null) orders.findLockedById(p.getOrder().getId()).orElseThrow();
        else if(p.getCustomOrder()!=null) customs.findLockedById(p.getCustomOrder().getId()).orElseThrow();
        return payments.findByTransactionId(code).orElseThrow();
    }
    private UUID owner(Payment p){ return p.getOrder()!=null?p.getOrder().getBuyer().getId():p.getCustomOrder().getBuyer().getId(); }
    private void settle(Payment p,BigDecimal amount){
        if(p.getAmount().compareTo(amount)!=0) throw bad("Số tiền giao dịch không khớp đơn hàng");
        if("SUCCESS".equals(p.getStatus()))return;
        p.setStatus("SUCCESS");p.setPaidAt(Instant.now());p.setUpdatedAt(Instant.now());
        User buyer;
        if(p.getOrder()!=null){var o=p.getOrder();if("CANCELLED".equals(o.getStatus()))throw bad("Đơn đã hủy, cần đối soát thanh toán");o.setStatus("PAID");o.setUpdatedAt(Instant.now());buyer=o.getBuyer();}
        else {var o=p.getCustomOrder();if("CANCELLED".equals(o.getStatus()))throw bad("Yêu cầu đã hủy");o.setStatus("PAID");o.setUpdatedAt(Instant.now());buyer=o.getBuyer();}
        payments.save(p); notifications.sendNotification(buyer,"Đã nhận thanh toán","Giao dịch " + p.getTransactionId() + " đã thanh toán thành công.", "PAYMENT", "/orders");
    }
    @Override public CreatePaymentLinkResponseDTO createPaymentLink(UUID userId,CreatePaymentLinkRequestDTO request){
        if(request.paymentOption()!=null && !"FULL".equals(request.paymentOption()))throw bad("Chỉ hỗ trợ thanh toán toàn bộ báo giá đã xác nhận");
        Order order=null; CustomOrder custom=null; BigDecimal total; Payment payment;
        if("ORDER".equals(request.orderType())){
            order=orders.findLockedById(request.orderId()).orElseThrow(()->bad("Không tìm thấy đơn hàng"));
            if(!order.getBuyer().getId().equals(userId))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            if(!"PENDING".equals(order.getStatus()))throw bad("Đơn không ở trạng thái chờ thanh toán");
            total=order.getTotalAmount(); payment=payments.findByOrderId(order.getId()).orElseGet(Payment::new);
        }else if("CUSTOM_ORDER".equals(request.orderType())){
            custom=customs.findLockedById(request.orderId()).orElseThrow(()->bad("Không tìm thấy yêu cầu"));
            if(!custom.getBuyer().getId().equals(userId))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            if(!"ACCEPTED".equals(custom.getStatus()))throw bad("Hãy chấp nhận báo giá trước khi thanh toán");
            total=custom.getQuotedPrice(); payment=payments.findByCustomOrderId(custom.getId()).orElseGet(Payment::new);
        }else throw bad("Loại đơn hàng không hợp lệ");
        if(total==null||total.signum()<=0)throw bad("Đơn chưa có số tiền thanh toán hợp lệ");
        if(request.customAmount()!=null && request.customAmount().compareTo(total)!=0)throw bad("Số tiền phải khớp giá từ hệ thống");
        if("SUCCESS".equals(payment.getStatus()))throw bad("Đơn đã thanh toán");
        if("COD".equals(payment.getGateway()))throw bad("Đơn đã chọn thanh toán khi nhận hàng");
        if(payment.getCheckoutUrl()!=null && "PENDING".equals(payment.getStatus())){
            var live=payOS.paymentRequests().get(Long.valueOf(payment.getTransactionId()));
            if("PAID".equals(String.valueOf(live.getStatus())))throw bad("Giao dịch đã thanh toán; hãy tải lại trạng thái đơn");
            if(payment.getExpiresAt()!=null && payment.getExpiresAt().isAfter(Instant.now()) && !"CANCELLED".equals(String.valueOf(live.getStatus())))return response(payment);
            if(!"CANCELLED".equals(String.valueOf(live.getStatus())))payOS.paymentRequests().cancel(Long.valueOf(payment.getTransactionId()),"Tạo lại liên kết hết hạn");
        }
        long code=UUID.randomUUID().getMostSignificantBits() & 0x1FFFFFFFFFFFFFL;
        Instant expiry=Instant.now().plusSeconds(900);
        var link=payOS.paymentRequests().create(CreatePaymentLinkRequest.builder().orderCode(code).amount(total.longValueExact())
            .description("PrintHub " + code).returnUrl(returnUrl.replace("/#/","/"))
            .cancelUrl(cancelUrl.replace("/#/","/")).expiredAt(expiry.getEpochSecond()).build());
        payment.setOrder(order);payment.setCustomOrder(custom);payment.setAmount(total);payment.setGateway("PAYOS");payment.setStatus("PENDING");
        payment.setTransactionId(String.valueOf(code));payment.setCheckoutUrl(link.getCheckoutUrl());payment.setExpiresAt(expiry);
        if(payment.getCreatedAt()==null)payment.setCreatedAt(Instant.now());payment.setUpdatedAt(Instant.now());payments.save(payment);
        if(custom!=null)custom.setPaymentMethod("PAYOS");
        return response(payment);
    }
    private CreatePaymentLinkResponseDTO response(Payment p){return CreatePaymentLinkResponseDTO.builder().paymentLinkUrl(p.getCheckoutUrl()).orderCode(p.getTransactionId()).amount(p.getAmount()).expiredAt(p.getExpiresAt()).build();}
    @Override public PayOSWebhookResponseDTO handleWebhook(Webhook request){
        WebhookData data;
        try{data=payOS.webhooks().verify(request);}catch(Exception e){throw bad("Chữ ký webhook không hợp lệ");}
        if(!"00".equals(data.getCode()))return PayOSWebhookResponseDTO.builder().success(true).message("Đã nhận thông báo chưa thanh toán").build();
        var p=lockPayment(String.valueOf(data.getOrderCode()));
        if(data.getAmount()==null)throw bad("Thiếu số tiền");
        settle(p,BigDecimal.valueOf(data.getAmount()));
        return PayOSWebhookResponseDTO.builder().success(true).message("Đã xác nhận thanh toán").build();
    }
    @Override public Map<String,Object> verify(String code,UUID userId){
        var p=lockPayment(code);if(!owner(p).equals(userId))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(!"SUCCESS".equals(p.getStatus())){
            var live=payOS.paymentRequests().get(Long.valueOf(code));
            if("PAID".equals(String.valueOf(live.getStatus())))settle(p,BigDecimal.valueOf(live.getAmountPaid()));
            else if("CANCELLED".equals(String.valueOf(live.getStatus()))){p.setStatus("FAILED");p.setUpdatedAt(Instant.now());payments.save(p);}
        }
        return Map.of("orderCode",code,"status","SUCCESS".equals(p.getStatus())?"PAID":p.getStatus(),"amount",p.getAmount());
    }
}