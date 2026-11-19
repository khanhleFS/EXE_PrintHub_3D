package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkResponseDTO;
import com.fpt.printhub_3d.dto.payment.PayOSWebhookResponseDTO;
import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.SubscriptionErrorCode;
import com.fpt.printhub_3d.entity.CheckoutOrder;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.SubscriptionPurchase;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CheckoutOrderRepository;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.repository.SubscriptionPurchaseRepository;
import com.fpt.printhub_3d.service.InventoryService;
import com.fpt.printhub_3d.service.NotificationService;
import com.fpt.printhub_3d.service.PaymentService;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import com.fpt.printhub_3d.service.UserSubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.payos.PayOS;
import vn.payos.exception.APIException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.fpt.printhub_3d.entity.Payment.PENDING_RECONCILIATION;

@Slf4j
@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final BigDecimal PAYOS_MIN_AMOUNT = new BigDecimal("2000");
    private static final ZoneId PAYOS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> CLOSED_STATUSES = Set.of("CANCELLED", "EXPIRED");
    private static final Set<String> FULFILLMENT_STATUSES = Set.of("PAID", "PREPARING", "PRINTING", "SHIPPING", "COMPLETED");

    private final OrderRepository orders;
    private final CustomOrderRepository customs;
    private final CheckoutOrderRepository checkoutOrders;
    private final PaymentRepository payments;
    private final SubscriptionPurchaseRepository subscriptionPurchases;
    private final UserSubscriptionService userSubscriptionService;
    private final PayOS payOS;
    private final NotificationService notifications;
    private final InventoryService inventoryService;
    private final SubscriptionBenefitService subscriptionBenefitService;
    private final OrderEmailEvents emailEvents;

    @Autowired
    public PaymentServiceImpl(
            OrderRepository orders,
            CustomOrderRepository customs,
            CheckoutOrderRepository checkoutOrders,
            PaymentRepository payments,
            SubscriptionPurchaseRepository subscriptionPurchases,
            UserSubscriptionService userSubscriptionService,
            PayOS payOS,
            NotificationService notifications,
            InventoryService inventoryService,
            @Autowired(required = false) SubscriptionBenefitService subscriptionBenefitService,
            OrderEmailEvents emailEvents
    ) {
        this.orders = orders;
        this.customs = customs;
        this.checkoutOrders = checkoutOrders;
        this.payments = payments;
        this.subscriptionPurchases = subscriptionPurchases;
        this.userSubscriptionService = userSubscriptionService;
        this.payOS = payOS;
        this.notifications = notifications;
        this.inventoryService = inventoryService;
        this.subscriptionBenefitService = subscriptionBenefitService;
        this.emailEvents = emailEvents;
    }

    public PaymentServiceImpl(
            OrderRepository orders,
            CustomOrderRepository customs,
            CheckoutOrderRepository checkoutOrders,
            PaymentRepository payments,
            SubscriptionPurchaseRepository subscriptionPurchases,
            UserSubscriptionService userSubscriptionService,
            PayOS payOS,
            NotificationService notifications,
            InventoryService inventoryService
    ) {
        this(
                orders,
                customs,
                checkoutOrders,
                payments,
                subscriptionPurchases,
                userSubscriptionService,
                payOS,
                notifications,
                inventoryService,
                null,
                null
        );
    }

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
        CheckoutOrder checkoutOrder = null;
        SubscriptionPurchase subscriptionPurchase = null;
        BigDecimal total;
        Payment payment;

        if ("ORDER".equals(request.orderType())) {
            order = orders.findLockedById(request.orderId())
                    .orElseThrow(() -> bad("Không tìm thấy đơn hàng"));
            if (!order.getBuyer().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            if (order.getCheckoutOrder() != null) {
                throw bad("Vui lòng thanh toán theo đơn checkout.");
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
            if (custom.getCheckoutOrder() != null) {
                throw bad("Vui lòng thanh toán theo đơn checkout.");
            }
            if (custom.getZeroAmountSettledAt() != null) {
                throw bad("Đơn đã được ưu đãi toàn bộ, số tiền phải thu là 0đ.");
            }
            if (!"ACCEPTED".equals(custom.getStatus())) {
                throw bad("Hãy chấp nhận báo giá trước khi thanh toán");
            }
            total = OrderPricing.customPayable(custom);
            payment = payments.findByCustomOrderId(custom.getId()).orElseGet(Payment::new);

        } else if ("CHECKOUT_ORDER".equals(request.orderType())) {
            checkoutOrder = checkoutOrders.findLockedById(request.orderId())
                    .orElseThrow(() -> bad("Không tìm thấy đơn thanh toán"));
            if (!checkoutOrder.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            if (checkoutOrder.getZeroAmountSettledAt() != null) {
                throw bad("Đơn đã được ưu đãi toàn bộ, số tiền phải thu là 0đ.");
            }
            if (!"CONFIRMED".equals(checkoutOrder.getStatus())) {
                throw bad("Đơn không ở trạng thái có thể thanh toán");
            }
            if ("COD".equalsIgnoreCase(checkoutOrder.getPaymentMethod())) {
                throw bad("Đơn đã chọn thanh toán khi nhận hàng");
            }
            List<CustomOrder> customsList = customs.findByCheckoutOrderId(checkoutOrder.getId());
            boolean hasUnacceptedCustom = customsList.stream()
                    .filter(c -> !"CANCELLED".equalsIgnoreCase(c.getStatus()))
                    .anyMatch(c -> !"ACCEPTED".equals(c.getStatus()) && !"PAID".equals(c.getStatus()));
            if (hasUnacceptedCustom) {
                throw bad("Đơn còn sản phẩm custom chưa được duyệt báo giá, không thể tạo liên kết thanh toán");
            }
            total = checkoutOrder.getTotalAmount();
            payment = payments.findByCheckoutOrderId(checkoutOrder.getId()).orElseGet(Payment::new);

        } else if ("SUBSCRIPTION_PURCHASE".equals(request.orderType()) || "SUBSCRIPTION".equals(request.orderType())) {
            subscriptionPurchase = subscriptionPurchases.findLockedById(request.orderId())
                    .orElseThrow(() -> bad("Không tìm thấy giao dịch mua gói hội viên"));
            if (!subscriptionPurchase.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            if (!"PENDING".equals(subscriptionPurchase.getStatus())) {
                throw bad("Giao dịch mua gói không ở trạng thái chờ thanh toán");
            }
            total = subscriptionPurchase.getAmount();
            payment = payments.findBySubscriptionPurchaseId(subscriptionPurchase.getId()).orElseGet(Payment::new);

        } else {
            throw bad("Loại đơn hàng không hợp lệ");
        }

        if (total == null || total.signum() <= 0) {
            throw bad("Đơn chưa có số tiền thanh toán hợp lệ");
        }
        if (total.compareTo(PAYOS_MIN_AMOUNT) < 0) {
            throw bad("Số tiền thanh toán PayOS sau ưu đãi phải tối thiểu 2.000 VND (hiện tại: " + total + " VND)");
        }
        if (request.customAmount() != null && request.customAmount().compareTo(total) != 0) {
            throw bad("Số tiền phải khớp giá từ hệ thống");
        }
        if ("SUCCESS".equals(payment.getStatus())) {
            throw bad("Đơn đã thanh toán");
        }
        if (PENDING_RECONCILIATION.equals(payment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Giao dịch đang chờ đối soát.");
        }
        if ("COD".equals(payment.getGateway())) {
            throw bad("Đơn đã chọn thanh toán khi nhận hàng");
        }

        boolean quotedPayment = checkoutOrder != null || custom != null || subscriptionPurchase != null;
        if (quotedPayment) {
            QuotePayment.requirePayable(total);
            if ("CANCELLED".equals(payment.getStatus()) || "EXPIRED".equals(payment.getStatus())
                    || (payment.getExpiresAt() != null && !payment.getExpiresAt().isAfter(Instant.now()))) {
                throw bad("Giao dịch đã hủy hoặc hết hạn");
            }
            if ("PENDING".equals(payment.getStatus()) && payment.getTransactionId() != null) {
                if (payment.getAmount().compareTo(total) != 0) {
                    throw bad("Số tiền giao dịch không khớp giá đã xác nhận");
                }
                if (payment.getCheckoutUrl() != null) {
                    return response(payment);
                }
            }
        } else if (payment.getCheckoutUrl() != null && "PENDING".equals(payment.getStatus())) {
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

        boolean prepared = quotedPayment && "PENDING".equals(payment.getStatus()) && payment.getTransactionId() != null;
        long code = prepared ? Long.parseLong(payment.getTransactionId()) : QuotePayment.nextOrderCode(payments);
        Instant expiry = prepared && payment.getExpiresAt() != null
                ? payment.getExpiresAt() : Instant.now().plusSeconds(900);

        String finalReturnUrl = (request.returnUrl() != null && !request.returnUrl().isBlank())
                ? request.returnUrl() : (returnUrl != null ? returnUrl : "http://localhost:5173/payment-result");
        String finalCancelUrl = (request.cancelUrl() != null && !request.cancelUrl().isBlank())
                ? request.cancelUrl() : (cancelUrl != null ? cancelUrl : "http://localhost:5173/payment-result?cancel=true");

        String description = (request.description() != null && !request.description().isBlank())
                ? request.description()
                : (subscriptionPurchase != null ? "SUB " + code : "PrintHub " + code);
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        var linkRequest = CreatePaymentLinkRequest.builder()
                .orderCode(code)
                .amount(total.setScale(0, java.math.RoundingMode.HALF_UP).longValue())
                .description(description)
                .returnUrl(finalReturnUrl.replace("/#/", "/"))
                .cancelUrl(finalCancelUrl.replace("/#/", "/"))
                .expiredAt(expiry.getEpochSecond())
                .build();
        String checkoutUrl;
        try {
            checkoutUrl = payOS.paymentRequests().create(linkRequest).getCheckoutUrl();
        } catch (RuntimeException createFailure) {
            if (!prepared) {
                throw createFailure;
            }
            // A timed-out create can have succeeded remotely. Recover only the same order code.
            PaymentLink live = payOS.paymentRequests().get(code);
            if (live == null || !Long.valueOf(code).equals(live.getOrderCode())
                    || !Long.valueOf(total.longValueExact()).equals(live.getAmount())
                    || live.getStatus() != PaymentLinkStatus.PENDING || live.getId() == null
                    || !live.getId().matches("[0-9a-fA-F-]{32,36}")) {
                throw createFailure;
            }
            checkoutUrl = "https://pay.payos.vn/web/" + live.getId();
        }

        payment.setOrder(order);
        payment.setCustomOrder(custom);
        payment.setCheckoutOrder(checkoutOrder);
        payment.setSubscriptionPurchase(subscriptionPurchase);
        payment.setAmount(total);
        payment.setGateway("PAYOS");
        payment.setStatus("PENDING");
        payment.setTransactionId(String.valueOf(code));
        payment.setCheckoutUrl(checkoutUrl);
        payment.setExpiresAt(expiry);

        if (payment.getCreatedAt() == null) {
            payment.setCreatedAt(Instant.now());
        }
        payment.setUpdatedAt(Instant.now());
        payments.save(payment);

        if (custom != null) {
            custom.setPaymentMethod("PAYOS");
        }
        if (checkoutOrder != null) {
            checkoutOrder.setPaymentMethod("PAYOS");
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

        if (!payments.existsByTransactionId(String.valueOf(data.getOrderCode()))) {
            return PayOSWebhookResponseDTO.builder()
                    .success(true)
                    .message("Webhook kiểm tra từ PayOS hoặc giao dịch không tồn tại: " + data.getOrderCode())
                    .build();
        }

        if (data.getOrderCode() == null || data.getAmount() == null || data.getAmount() <= 0
                || (data.getCurrency() != null && !"VND".equalsIgnoreCase(data.getCurrency()))) {
            throw bad("Thông tin số tiền PayOS không hợp lệ");
        }
        Payment payment = lockPayment(String.valueOf(data.getOrderCode()));
        requirePayos(payment);
        PaymentLink live = fetchPayosLink(data.getOrderCode().toString());
        if (live == null || live.getAmountPaid() == null || live.getAmountPaid() < data.getAmount()
                || (data.getPaymentLinkId() != null && !data.getPaymentLinkId().equals(live.getId()))
                || !containsWebhookTransfer(live, data)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Thông tin thu tiền từ PayOS chưa sẵn sàng, vui lòng gửi lại webhook.");
        }
        processPayosLink(payment, live, parsePayosTime(data.getTransactionDateTime()));

        return PayOSWebhookResponseDTO.builder()
                .success(true)
                .message("SUCCESS".equals(payment.getStatus()) && payment.getReconciliationAmount() == null
                        ? "Đã xác nhận thanh toán" : "Đã ghi nhận giao dịch để đối soát")
                .build();
    }

    @Override
    public Map<String, Object> verify(String code, UUID userId) {
        Payment payment = lockPayment(code);
        if (!owner(payment).equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        requirePayos(payment);
        PaymentLink live = fetchPayosLink(code);
        if (live != null) {
            processPayosLink(payment, live, null);
        }

        String targetType = "ORDER";
        String planName = null;
        if (payment.getSubscriptionPurchase() != null) {
            targetType = "SUBSCRIPTION_PURCHASE";
            if (payment.getSubscriptionPurchase().getPlan() != null) {
                planName = payment.getSubscriptionPurchase().getPlan().getName();
            }
        } else if (payment.getCustomOrder() != null) {
            targetType = "CUSTOM_ORDER";
        } else if (payment.getCheckoutOrder() != null) {
            targetType = "CHECKOUT_ORDER";
        }

        Map<String, Object> responseMap = new java.util.HashMap<>();
        responseMap.put("orderCode", code);
        responseMap.put("status", "SUCCESS".equals(payment.getStatus()) ? "PAID" : payment.getStatus());
        responseMap.put("amount", payment.getAmount());
        if (payment.getReconciliationAmount() != null) {
            responseMap.put("reconciliationAmount", payment.getReconciliationAmount());
            responseMap.put("reconciliationReceivedAt", payment.getReconciliationReceivedAt());
            responseMap.put("reconciliationReason", payment.getReconciliationReason());
        }
        responseMap.put("targetType", targetType);
        if (payment.getSubscriptionPurchase() != null) {
            responseMap.put("purchaseId", payment.getSubscriptionPurchase().getId());
            responseMap.put("purchaseStatus", payment.getSubscriptionPurchase().getStatus());
        }
        if (planName != null) {
            responseMap.put("planName", planName);
        }
        responseMap.put("verifiedAt", Instant.now().toString());
        return responseMap;
    }

    private void requirePayos(Payment payment) {
        if (!"PAYOS".equalsIgnoreCase(payment.getGateway())) {
            throw bad("Giao dịch không thuộc PayOS");
        }
    }

    private PaymentLink fetchPayosLink(String orderCode) {
        try {
            long code = Long.parseLong(orderCode);
            PaymentLink live = payOS.paymentRequests().get(code);
            return live != null && Long.valueOf(code).equals(live.getOrderCode()) ? live : null;
        } catch (Exception ex) {
            log.warn("Chưa đọc được trạng thái PayOS cho mã {}", orderCode);
            return null;
        }
    }

    private boolean containsWebhookTransfer(PaymentLink live, WebhookData webhook) {
        String reference = webhook.getReference();
        return reference == null || reference.isBlank() || (live.getTransactions() != null
                && live.getTransactions().stream().filter(Objects::nonNull)
                        .anyMatch(tx -> reference.equals(tx.getReference()) && webhook.getAmount().equals(tx.getAmount())));
    }

    private void processPayosLink(Payment payment, PaymentLink live, Instant fallbackTime) {
        if (live.getAmountPaid() != null && live.getAmountPaid() > 0) {
            Instant receivedAt = live.getTransactions() == null ? null : live.getTransactions().stream()
                    .filter(Objects::nonNull).map(tx -> parsePayosTime(tx.getTransactionDateTime()))
                    .filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
            if (receivedAt == null) {
                receivedAt = fallbackTime != null ? fallbackTime : Instant.now();
            }
            settle(payment, BigDecimal.valueOf(live.getAmountPaid()),
                    receivedAt, live.getStatus() == PaymentLinkStatus.PAID);
        } else if (canCloseQuote(live) && "PENDING".equals(payment.getStatus())) {
            String status = live.getStatus() == PaymentLinkStatus.EXPIRED ? "EXPIRED" : "CANCELLED";
            if (isQuotePayment(payment)) {
                closeQuotePayment(payment, status);
            } else {
                closeStandalonePayment(payment, status);
            }
        }
    }

    private Instant parsePayosTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value.trim().replace(' ', 'T')).atZone(PAYOS_ZONE).toInstant();
            } catch (DateTimeParseException invalid) {
                return null;
            }
        }
    }

    private void closeStandalonePayment(Payment payment, String status) {
        Instant now = Instant.now();
        payment.setStatus(status);
        payment.setUpdatedAt(now);
        payments.save(payment);
        SubscriptionPurchase purchase = payment.getSubscriptionPurchase();
        if (purchase != null && "PENDING".equals(purchase.getStatus())) {
            purchase.setStatus(status);
            subscriptionPurchases.save(purchase);
        }
        Order order = payment.getOrder();
        if (order != null && order.getCheckoutOrder() == null && "PENDING".equals(order.getStatus())) {
            order.setStatus(status);
            order.setUpdatedAt(now);
            orders.save(order);
            inventoryService.releaseActiveReservations(order.getId(), "Giao dịch PayOS đã đóng", payment.getTransactionId());
            if (subscriptionBenefitService != null) {
                subscriptionBenefitService.releaseReservedBenefits(SubscriptionBenefitService.UsageTargetType.ORDER, order.getId());
            }
        }
    }

    // --- Private Helper Methods ---

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    @Override
    public void cancelQuotePayment(String orderCode, String reason) {
        Payment payment = lockPayment(orderCode);
        if (!isQuotePayment(payment) || !"PENDING".equals(payment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Giao dịch không còn ở trạng thái chờ thanh toán.");
        }
        PaymentLink live = quoteLinkForClosing(orderCode, reason);
        if (live != null && !canCloseQuote(live)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PayOS chưa xác nhận giao dịch có thể hủy.");
        }
        payment.setStatus("CANCELLED");
        payment.setUpdatedAt(Instant.now());
        payments.save(payment);
    }

    @Override
    public void expireQuotePayment(String orderCode) {
        Payment payment = lockPayment(orderCode);
        if ((!isQuotePayment(payment) && payment.getSubscriptionPurchase() == null) || !"PENDING".equals(payment.getStatus())
                || payment.getExpiresAt() == null || payment.getExpiresAt().isAfter(Instant.now())) {
            return;
        }
        PaymentLink live = quoteLinkForClosing(orderCode, "Hết hạn thanh toán");
        if (live != null && live.getAmountPaid() != null && live.getAmountPaid() > 0) {
            processPayosLink(payment, live, null);
        } else if (live == null || canCloseQuote(live)) {
            if (isQuotePayment(payment)) {
                closeQuotePayment(payment, "EXPIRED");
            } else {
                closeStandalonePayment(payment, "EXPIRED");
            }
        }
    }

    private PaymentLink quoteLinkForClosing(String orderCode, String reason) {
        long code = Long.parseLong(orderCode);
        PaymentLink live;
        try {
            live = payOS.paymentRequests().get(code);
        } catch (APIException ex) {
            if (ex.getStatusCode().orElse(0) == HttpStatus.NOT_FOUND.value()) {
                return null;
            }
            throw ex;
        }
        if (live == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Chưa xác minh được trạng thái PayOS.");
        }
        if (live.getStatus() == PaymentLinkStatus.PENDING && Long.valueOf(0).equals(live.getAmountPaid())) {
            PaymentLink cancelled = payOS.paymentRequests().cancel(code, reason);
            if (cancelled == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Chưa xác minh được kết quả hủy PayOS.");
            }
            return cancelled;
        }
        return live;
    }

    private boolean isQuotePayment(Payment payment) {
        return payment.getCheckoutOrder() != null || payment.getCustomOrder() != null;
    }

    private boolean canCloseQuote(PaymentLink live) {
        return live != null && Long.valueOf(0).equals(live.getAmountPaid())
                && (live.getStatus() == PaymentLinkStatus.CANCELLED || live.getStatus() == PaymentLinkStatus.EXPIRED
                        || live.getStatus() == PaymentLinkStatus.FAILED);
    }

    private void closeQuotePayment(Payment payment, String status) {
        if (!"PENDING".equals(payment.getStatus())) {
            return;
        }
        Instant now = Instant.now();
        payment.setStatus(status);
        payment.setUpdatedAt(now);
        payments.save(payment);

        CheckoutOrder checkout = payment.getCheckoutOrder();
        if (checkout != null && "CONFIRMED".equals(checkout.getStatus())) {
            checkout.setStatus(status);
            checkout.setUpdatedAt(now);
            checkoutOrders.save(checkout);
            if (subscriptionBenefitService != null) {
                subscriptionBenefitService.releaseReservedBenefits(
                        SubscriptionBenefitService.UsageTargetType.CHECKOUT_ORDER, checkout.getId());
            }
            for (Order order : orders.findByCheckoutOrderId(checkout.getId())) {
                if ("PENDING".equals(order.getStatus())) {
                    order.setStatus(status);
                    order.setUpdatedAt(now);
                    orders.save(order);
                    inventoryService.releaseActiveReservations(order.getId(), "Giao dịch PayOS đã đóng", payment.getTransactionId());
                }
            }
            for (CustomOrder custom : customs.findByCheckoutOrderId(checkout.getId())) {
                if ("ACCEPTED".equals(custom.getStatus())) {
                    custom.setStatus(status);
                    custom.setUpdatedAt(now);
                    customs.save(custom);
                }
            }
        } else if (payment.getCustomOrder() != null && payment.getCustomOrder().getCheckoutOrder() == null
                && "ACCEPTED".equals(payment.getCustomOrder().getStatus())) {
            CustomOrder custom = payment.getCustomOrder();
            custom.setStatus(status);
            custom.setUpdatedAt(now);
            customs.save(custom);
            if (subscriptionBenefitService != null) {
                subscriptionBenefitService.releaseReservedBenefits(
                        SubscriptionBenefitService.UsageTargetType.CUSTOM_ORDER, custom.getId());
            }
        }
    }

    private Payment lockPayment(String code) {
        PaymentRepository.PaymentTarget target = payments.findTargetByTransactionId(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch"));

        if (target.getParentCheckoutOrderId() != null) {
            checkoutOrders.findLockedById(target.getParentCheckoutOrderId()).orElseThrow();
        }
        if (target.getOrderId() != null) {
            orders.findLockedById(target.getOrderId()).orElseThrow();
        } else if (target.getCustomOrderId() != null) {
            customs.findLockedById(target.getCustomOrderId()).orElseThrow();
        } else if (target.getCheckoutOrderId() != null) {
            checkoutOrders.findLockedById(target.getCheckoutOrderId()).orElseThrow();
        } else if (target.getSubscriptionPurchaseId() != null) {
            subscriptionPurchases.findLockedById(target.getSubscriptionPurchaseId()).orElseThrow();
        }

        return payments.findByTransactionId(code).orElseThrow();
    }

    private UUID owner(Payment payment) {
        if (payment.getOrder() != null) {
            return payment.getOrder().getBuyer().getId();
        } else if (payment.getCustomOrder() != null) {
            return payment.getCustomOrder().getBuyer().getId();
        } else if (payment.getCheckoutOrder() != null) {
            return payment.getCheckoutOrder().getUser().getId();
        } else if (payment.getSubscriptionPurchase() != null) {
            return payment.getSubscriptionPurchase().getUser().getId();
        }
        throw bad("Giao dịch không có thông tin người sở hữu");
    }

    private void settle(Payment payment, BigDecimal received, Instant receivedAt, boolean confirmedPaid) {
        String targetStatus = targetStatus(payment);
        if (isCheckoutChild(payment)) {
            flagReconciliation(payment, received, receivedAt, "DUPLICATE_CHECKOUT_SOURCE", false);
            return;
        }
        if ((targetStatus != null && CLOSED_STATUSES.contains(targetStatus))
                || (payment.getStatus() != null && CLOSED_STATUSES.contains(payment.getStatus()))
                || "LATE_PAYMENT".equals(payment.getReconciliationReason())) {
            flagReconciliation(payment, received, receivedAt, "LATE_PAYMENT", false);
            return;
        }
        BigDecimal expected = payment.getAmount();
        BigDecimal targetAmount = targetAmount(payment);
        if (expected == null || (targetAmount != null && expected.compareTo(targetAmount) != 0)) {
            flagReconciliation(payment, received, receivedAt, "AMOUNT_MISMATCH", false);
            return;
        }
        if ("SUCCESS".equals(payment.getStatus())) {
            BigDecimal excess = received.subtract(expected);
            if (excess.signum() > 0) {
                flagReconciliation(payment, excess, receivedAt, "EXCESS_AFTER_SETTLEMENT", true);
            }
            return;
        }

        if (!isPayableTarget(payment, targetStatus)) {
            flagReconciliation(payment, received, receivedAt, "UNPAYABLE_TARGET", false);
            return;
        }
        if (expected.compareTo(received) != 0) {
            flagReconciliation(payment, received, receivedAt, "AMOUNT_MISMATCH", false);
            return;
        }
        if (!confirmedPaid) {
            flagReconciliation(payment, received, receivedAt, "PAYOS_NOT_CONFIRMED", false);
            return;
        }

        User buyer = payment.getOrder() != null ? payment.getOrder().getBuyer()
                : payment.getCustomOrder() != null ? payment.getCustomOrder().getBuyer()
                : payment.getCheckoutOrder() != null ? payment.getCheckoutOrder().getUser()
                : payment.getSubscriptionPurchase().getUser();
        Instant now = Instant.now();
        String notificationMessage = "Giao dịch " + payment.getTransactionId() + " đã thanh toán thành công.";
        String notificationLink = "/orders";

        if (payment.getOrder() != null) {
            Order order = payment.getOrder();
            if ("PENDING".equals(order.getStatus())) {
                order.setStatus("PAID");
                order.setUpdatedAt(now);
                orders.save(order);
            }
            inventoryService.confirmReservation(order.getId());
            commitBenefits(SubscriptionBenefitService.UsageTargetType.ORDER, order.getId());
        } else if (payment.getCustomOrder() != null) {
            CustomOrder custom = payment.getCustomOrder();
            if ("ACCEPTED".equals(custom.getStatus())) {
                custom.setStatus("PAID");
                custom.setUpdatedAt(now);
                customs.save(custom);
            }
            commitBenefits(SubscriptionBenefitService.UsageTargetType.CUSTOM_ORDER, custom.getId());
        } else if (payment.getCheckoutOrder() != null) {
            settleCheckout(payment.getCheckoutOrder(), now);
        } else {
            SubscriptionPurchase purchase = payment.getSubscriptionPurchase();
            try {
                userSubscriptionService.activateSubscriptionFromPurchase(purchase);
            } catch (ApiException ex) {
                if (ex.getErrorCode() != SubscriptionErrorCode.ACTIVE_PLAN_CONFLICT) {
                    throw ex;
                }
                flagReconciliation(payment, received, receivedAt, "ACTIVE_PLAN_CONFLICT", false);
                return;
            }
            purchase.setSettledAt(receivedAt);
            notificationMessage = "Gói hội viên " + purchase.getPlan().getName() + " đã được kích hoạt thành công.";
            notificationLink = "/profile";
        }

        payment.setStatus("SUCCESS");
        payment.setPaidAt(receivedAt);
        payment.setUpdatedAt(now);
        payment.setReconciliationAmount(null);
        payment.setReconciliationReason(null);
        payment.setReconciliationReceivedAt(null);
        payments.save(payment);
        if (emailEvents != null) {
            emailEvents.enqueueReceipt(payment);
        }
        notifications.sendNotification(buyer, "Đã nhận thanh toán", notificationMessage, "PAYMENT", notificationLink);
    }

    private void settleCheckout(CheckoutOrder checkout, Instant now) {
        if ("CONFIRMED".equals(checkout.getStatus())) {
            checkout.setStatus("PAID");
            checkout.setUpdatedAt(now);
            checkoutOrders.save(checkout);
        }
        for (Order order : orders.findByCheckoutOrderId(checkout.getId())) {
            if (!CLOSED_STATUSES.contains(order.getStatus())) {
                if ("PENDING".equals(order.getStatus())) {
                    order.setStatus("PAID");
                    order.setUpdatedAt(now);
                    orders.save(order);
                }
                inventoryService.confirmReservation(order.getId());
            }
        }
        for (CustomOrder custom : customs.findByCheckoutOrderId(checkout.getId())) {
            if ("ACCEPTED".equals(custom.getStatus()) && custom.getQuotedPrice() != null) {
                custom.setStatus("PAID");
                custom.setUpdatedAt(now);
                customs.save(custom);
            }
        }
        commitBenefits(SubscriptionBenefitService.UsageTargetType.CHECKOUT_ORDER, checkout.getId());
    }

    private void commitBenefits(SubscriptionBenefitService.UsageTargetType type, UUID id) {
        if (subscriptionBenefitService != null) {
            subscriptionBenefitService.commitAppliedBenefits(type, id);
        }
    }

    private boolean isCheckoutChild(Payment payment) {
        return (payment.getOrder() != null && payment.getOrder().getCheckoutOrder() != null)
                || (payment.getCustomOrder() != null && payment.getCustomOrder().getCheckoutOrder() != null);
    }

    private String targetStatus(Payment payment) {
        if (payment.getOrder() != null) {
            return payment.getOrder().getStatus();
        }
        if (payment.getCustomOrder() != null) {
            return payment.getCustomOrder().getStatus();
        }
        if (payment.getCheckoutOrder() != null) {
            return payment.getCheckoutOrder().getStatus();
        }
        return payment.getSubscriptionPurchase() != null ? payment.getSubscriptionPurchase().getStatus() : "";
    }

    private BigDecimal targetAmount(Payment payment) {
        if (payment.getOrder() != null) {
            return payment.getOrder().getTotalAmount();
        }
        if (payment.getCheckoutOrder() != null) {
            return payment.getCheckoutOrder().getTotalAmount();
        }
        if (payment.getCustomOrder() != null) {
            return OrderPricing.customPayable(payment.getCustomOrder());
        }
        return payment.getSubscriptionPurchase() != null ? payment.getSubscriptionPurchase().getAmount() : null;
    }

    private boolean isPayableTarget(Payment payment, String targetStatus) {
        if (targetStatus == null) {
            return false;
        }
        if (payment.getSubscriptionPurchase() != null) {
            return "PENDING".equals(targetStatus);
        }
        if (payment.getCheckoutOrder() != null) {
            return payment.getCheckoutOrder().getZeroAmountSettledAt() == null
                    && ("CONFIRMED".equals(targetStatus) || FULFILLMENT_STATUSES.contains(targetStatus));
        }
        if (payment.getCustomOrder() != null) {
            return payment.getCustomOrder().getZeroAmountSettledAt() == null
                    && ("ACCEPTED".equals(targetStatus) || FULFILLMENT_STATUSES.contains(targetStatus));
        }
        return payment.getOrder() != null && ("PENDING".equals(targetStatus) || FULFILLMENT_STATUSES.contains(targetStatus));
    }

    private void flagReconciliation(Payment payment, BigDecimal amount, Instant receivedAt,
                                    String reason, boolean preserveSettlement) {
        BigDecimal recorded = payment.getReconciliationAmount();
        if (recorded != null && recorded.compareTo(amount) >= 0 && reason.equals(payment.getReconciliationReason())) {
            return;
        }
        if (!preserveSettlement) {
            payment.setStatus(PENDING_RECONCILIATION);
            payment.setPaidAt(null);
        }
        if (recorded == null || amount.compareTo(recorded) > 0) {
            payment.setReconciliationAmount(amount);
            payment.setReconciliationReceivedAt(receivedAt);
        }
        payment.setReconciliationReason(reason);
        payment.setUpdatedAt(Instant.now());
        payments.save(payment);
        log.warn("Giao dịch PayOS {} chờ đối soát: {}", payment.getTransactionId(), reason);
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
