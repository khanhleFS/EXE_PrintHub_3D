package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkResponseDTO;
import com.fpt.printhub_3d.dto.payment.PayOSWebhookResponseDTO;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.service.NotificationService;
import com.fpt.printhub_3d.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orders;
    private final CustomOrderRepository customs;
    private final PaymentRepository payments;
    private final PayOS payOS;
    private final NotificationService notifications;

    @Value("${payos.return-url:http://localhost:5173/payment-result}")
    private String returnUrl;

    @Value("${payos.cancel-url:http://localhost:5173/payment-result?cancel=true}")
    private String cancelUrl;

    @Override
    public CreatePaymentLinkResponseDTO createPaymentLink(UUID userId, CreatePaymentLinkRequestDTO request) {
        if (request.paymentOption() != null && !"FULL".equals(request.paymentOption())) {
            throw bad("Chỉ hỗ trợ thanh toán toàn bộ báo giá đã xác nhận");
        }

        Order order = null;
        CustomOrder custom = null;
        BigDecimal total;
        Payment payment;

        if ("ORDER".equals(request.orderType())) {
            order = orders.findLockedById(request.orderId())
                    .orElseThrow(() -> bad("Không tìm thấy đơn hàng"));
            if (!order.getBuyer().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            if (!"PENDING".equals(order.getStatus())) {
                throw bad("Đơn không ở trạng thái chờ thanh toán");
            }
            total = order.getTotalAmount();
            payment = payments.findByOrderId(order.getId()).orElseGet(Payment::new);

        } else if ("CUSTOM_ORDER".equals(request.orderType())) {
            custom = customs.findLockedById(request.orderId())
                    .orElseThrow(() -> bad("Không tìm thấy yêu cầu"));
            if (!custom.getBuyer().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            if (!"ACCEPTED".equals(custom.getStatus())) {
                throw bad("Hãy chấp nhận báo giá trước khi thanh toán");
            }
            total = custom.getQuotedPrice();
            payment = payments.findByCustomOrderId(custom.getId()).orElseGet(Payment::new);

        } else {
            throw bad("Loại đơn hàng không hợp lệ");
        }

        if (total == null || total.signum() <= 0) {
            throw bad("Đơn chưa có số tiền thanh toán hợp lệ");
        }
        if (request.customAmount() != null && request.customAmount().compareTo(total) != 0) {
            throw bad("Số tiền phải khớp giá từ hệ thống");
        }
        if ("SUCCESS".equals(payment.getStatus())) {
            throw bad("Đơn đã thanh toán");
        }
        if ("COD".equals(payment.getGateway())) {
            throw bad("Đơn đã chọn thanh toán khi nhận hàng");
        }

        if (payment.getCheckoutUrl() != null && "PENDING".equals(payment.getStatus())) {
            try {
                var live = payOS.paymentRequests().get(Long.valueOf(payment.getTransactionId()));
                String liveStatus = live != null ? String.valueOf(live.getStatus()) : "UNKNOWN";

                if ("PAID".equals(liveStatus)) {
                    throw bad("Giao dịch đã thanh toán; hãy tải lại trạng thái đơn");
                }

                boolean isNotExpired = payment.getExpiresAt() != null && payment.getExpiresAt().isAfter(Instant.now());
                if (isNotExpired && !"CANCELLED".equals(liveStatus)) {
                    return response(payment);
                }

                if (!"CANCELLED".equals(liveStatus)) {
                    try {
                        payOS.paymentRequests().cancel(Long.valueOf(payment.getTransactionId()), "Tạo lại liên kết hết hạn");
                    } catch (Exception ignored) {
                    }
                }
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception ignored) {
                // Nếu PayOS lỗi khi tra cứu link cũ, bỏ qua và tạo link thanh toán mới
            }
        }

        long code = UUID.randomUUID().getMostSignificantBits() & 0x1FFFFFFFFFFFFFL;
        if (code <= 0) {
            code = System.currentTimeMillis();
        }
        while (payments.findByTransactionId(String.valueOf(code)).isPresent()) {
            code = (UUID.randomUUID().getMostSignificantBits() & 0x1FFFFFFFFFFFFFL) + 1;
        }
        Instant expiry = Instant.now().plusSeconds(900);

        String finalReturnUrl = (request.returnUrl() != null && !request.returnUrl().isBlank())
                ? request.returnUrl() : returnUrl;
        String finalCancelUrl = (request.cancelUrl() != null && !request.cancelUrl().isBlank())
                ? request.cancelUrl() : cancelUrl;

        String description = (request.description() != null && !request.description().isBlank())
                ? request.description()
                : "PrintHub " + code;
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        var link = payOS.paymentRequests().create(CreatePaymentLinkRequest.builder()
                .orderCode(code)
                .amount(total.setScale(0, java.math.RoundingMode.HALF_UP).longValue())
                .description(description)
                .returnUrl(finalReturnUrl.replace("/#/", "/"))
                .cancelUrl(finalCancelUrl.replace("/#/", "/"))
                .expiredAt(expiry.getEpochSecond())
                .build());

        payment.setOrder(order);
        payment.setCustomOrder(custom);
        payment.setAmount(total);
        payment.setGateway("PAYOS");
        payment.setStatus("PENDING");
        payment.setTransactionId(String.valueOf(code));
        payment.setCheckoutUrl(link.getCheckoutUrl());
        payment.setExpiresAt(expiry);

        if (payment.getCreatedAt() == null) {
            payment.setCreatedAt(Instant.now());
        }
        payment.setUpdatedAt(Instant.now());
        payments.save(payment);

        if (custom != null) {
            custom.setPaymentMethod("PAYOS");
        }

        return response(payment);
    }

    @Override
    public PayOSWebhookResponseDTO handleWebhook(Webhook request) {
        WebhookData data;
        try {
            data = payOS.webhooks().verify(request);
        } catch (Exception e) {
            throw bad("Chữ ký webhook không hợp lệ");
        }

        if (!"00".equals(data.getCode())) {
            return PayOSWebhookResponseDTO.builder()
                    .success(true)
                    .message("Đã nhận thông báo chưa thanh toán")
                    .build();
        }

        // Hỗ trợ trường hợp PayOS gửi webhook kiểm tra (test webhook) hoặc đơn không tồn tại
        var existing = payments.findByTransactionId(String.valueOf(data.getOrderCode()));
        if (existing.isEmpty()) {
            return PayOSWebhookResponseDTO.builder()
                    .success(true)
                    .message("Webhook kiểm tra từ PayOS hoặc giao dịch không tồn tại: " + data.getOrderCode())
                    .build();
        }

        Payment payment = lockPayment(String.valueOf(data.getOrderCode()));
        if (data.getAmount() == null) {
            throw bad("Thiếu số tiền");
        }

        settle(payment, BigDecimal.valueOf(data.getAmount()));

        return PayOSWebhookResponseDTO.builder()
                .success(true)
                .message("Đã xác nhận thanh toán")
                .build();
    }

    @Override
    public Map<String, Object> verify(String code, UUID userId) {
        Payment payment = lockPayment(code);
        if (!owner(payment).equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (!"SUCCESS".equals(payment.getStatus())) {
            try {
                var live = payOS.paymentRequests().get(Long.valueOf(code));
                String liveStatus = live != null ? String.valueOf(live.getStatus()) : "UNKNOWN";

                if ("PAID".equals(liveStatus)) {
                    settle(payment, BigDecimal.valueOf(live.getAmountPaid()));
                } else if ("CANCELLED".equals(liveStatus)) {
                    payment.setStatus("CANCELLED");
                    payment.setUpdatedAt(Instant.now());
                    payments.save(payment);
                }
            } catch (Exception ignored) {
                // Tránh lỗi 500 nếu PayOS gặp gián đoạn tạm thời
            }
        }

        return Map.of(
                "orderCode", code,
                "status", "SUCCESS".equals(payment.getStatus()) ? "PAID" : payment.getStatus(),
                "amount", payment.getAmount()
        );
    }

    // --- Private Helper Methods ---

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private Payment lockPayment(String code) {
        Payment payment = payments.findByTransactionId(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch"));

        if (payment.getOrder() != null) {
            orders.findLockedById(payment.getOrder().getId()).orElseThrow();
        } else if (payment.getCustomOrder() != null) {
            customs.findLockedById(payment.getCustomOrder().getId()).orElseThrow();
        }

        return payments.findByTransactionId(code).orElseThrow();
    }

    private UUID owner(Payment payment) {
        return payment.getOrder() != null
                ? payment.getOrder().getBuyer().getId()
                : payment.getCustomOrder().getBuyer().getId();
    }

    private void settle(Payment payment, BigDecimal amount) {
        if (payment.getAmount().compareTo(amount) != 0) {
            throw bad("Số tiền giao dịch không khớp đơn hàng");
        }
        if ("SUCCESS".equals(payment.getStatus())) {
            return;
        }

        payment.setStatus("SUCCESS");
        payment.setPaidAt(Instant.now());
        payment.setUpdatedAt(Instant.now());

        User buyer;
        if (payment.getOrder() != null) {
            Order order = payment.getOrder();
            if ("CANCELLED".equals(order.getStatus())) {
                throw bad("Đơn đã hủy, cần đối soát thanh toán");
            }
            order.setStatus("PAID");
            order.setUpdatedAt(Instant.now());
            orders.save(order);
            buyer = order.getBuyer();
        } else {
            CustomOrder customOrder = payment.getCustomOrder();
            if ("CANCELLED".equals(customOrder.getStatus())) {
                throw bad("Yêu cầu đã hủy");
            }
            customOrder.setStatus("PAID");
            customOrder.setUpdatedAt(Instant.now());
            customs.save(customOrder);
            buyer = customOrder.getBuyer();
        }

        payments.save(payment);
        notifications.sendNotification(
                buyer,
                "Đã nhận thanh toán",
                "Giao dịch " + payment.getTransactionId() + " đã thanh toán thành công.",
                "PAYMENT",
                "/orders"
        );
    }

    private CreatePaymentLinkResponseDTO response(Payment payment) {
        return CreatePaymentLinkResponseDTO.builder()
                .paymentLinkUrl(payment.getCheckoutUrl())
                .orderCode(payment.getTransactionId())
                .amount(payment.getAmount())
                .expiredAt(payment.getExpiresAt())
                .build();
    }
}