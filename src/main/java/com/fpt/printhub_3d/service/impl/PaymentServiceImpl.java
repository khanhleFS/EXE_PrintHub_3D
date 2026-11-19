package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkResponseDTO;
import com.fpt.printhub_3d.dto.payment.PayOSWebhookResponseDTO;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final BigDecimal PAYOS_MIN_AMOUNT = new BigDecimal("2000");

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
            @Autowired(required = false) SubscriptionBenefitService subscriptionBenefitService
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
            BigDecimal lineTotal = custom.getQuotedPrice();
            if ((lineTotal == null || lineTotal.signum() <= 0) && custom.getUnitQuotedPrice() != null) {
                int quantity = custom.getQuantity() != null && custom.getQuantity() > 0 ? custom.getQuantity() : 1;
                lineTotal = custom.getUnitQuotedPrice().multiply(BigDecimal.valueOf(quantity));
            }
            total = custom.getSubtotalAmount() != null
                    ? OrderPricing.totalPayable(custom.getSubtotalAmount(), custom.getDiscountAmount(),
                            custom.getShippingFee(), custom.getShippingDiscountAmount())
                    : lineTotal;
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
        if ("COD".equals(payment.getGateway())) {
            throw bad("Đơn đã chọn thanh toán khi nhận hàng");
        }

        boolean quotedPayment = checkoutOrder != null || custom != null;
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
                } else if (isQuotePayment(payment)) {
                    if (canCloseQuote(live)) {
                        closeQuotePayment(payment, "EXPIRED".equals(liveStatus) ? "EXPIRED" : "CANCELLED");
                    }
                } else if ("CANCELLED".equals(liveStatus)) {
                    payment.setStatus("CANCELLED");
                    payment.setUpdatedAt(Instant.now());
                    payments.save(payment);

                    // Đồng bộ hủy SubscriptionPurchase nguyên tử
                    if (payment.getSubscriptionPurchase() != null) {
                        SubscriptionPurchase purchase = payment.getSubscriptionPurchase();
                        if (!"PAID".equals(purchase.getStatus())) {
                            purchase.setStatus("CANCELLED");
                            purchase.setSettledAt(Instant.now());
                            subscriptionPurchases.save(purchase);
                            log.info("Đã đồng bộ hủy SubscriptionPurchase [{}] theo cổng PayOS", purchase.getId());
                        }
                    }

                    // Đồng bộ hủy Order đơn lẻ và trả kho
                    if (payment.getOrder() != null) {
                        Order order = payment.getOrder();
                        if ("PENDING".equals(order.getStatus())) {
                            order.setStatus("CANCELLED");
                            order.setUpdatedAt(Instant.now());
                            orders.save(order);
                            inventoryService.releaseActiveReservations(order.getId(), "Hủy giao dịch cổng PayOS", "PAYOS_CANCEL_" + payment.getId());
                            if (subscriptionBenefitService != null) {
                                subscriptionBenefitService.releaseReservedBenefits(
                                        SubscriptionBenefitService.UsageTargetType.ORDER, order.getId());
                            }
                            log.info("Đã đồng bộ hủy Order [{}] và giải phóng kho theo cổng PayOS", order.getId());
                        }
                    }

                }
            } catch (Exception ignored) {
                // Tránh lỗi 500 nếu PayOS gặp gián đoạn tạm thời
            }
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
        if (!isQuotePayment(payment) || !"PENDING".equals(payment.getStatus())
                || payment.getExpiresAt() == null || payment.getExpiresAt().isAfter(Instant.now())) {
            return;
        }
        PaymentLink live = quoteLinkForClosing(orderCode, "Hết hạn thanh toán");
        if (live != null && live.getStatus() == PaymentLinkStatus.PAID && live.getAmountPaid() != null) {
            settle(payment, BigDecimal.valueOf(live.getAmountPaid()));
        } else if (live == null || canCloseQuote(live)) {
            closeQuotePayment(payment, "EXPIRED");
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
        String notificationMessage = "Giao dịch " + payment.getTransactionId() + " đã thanh toán thành công.";
        String notificationLink = "/orders";

        if (payment.getOrder() != null) {
            Order order = payment.getOrder();
            if ("CANCELLED".equals(order.getStatus()) || "EXPIRED".equals(order.getStatus())) {
                log.warn("Đơn hàng {} đã ở trạng thái {}, giao dịch PayOS {} ghi nhận thanh toán muộn cần đối soát",
                        order.getId(), order.getStatus(), payment.getTransactionId());
                buyer = order.getBuyer();
            } else {
                // Không kéo lùi tiến trình nếu đơn đã bước vào sản xuất / giao hàng
                if ("PENDING".equals(order.getStatus())) {
                    order.setStatus("PAID");
                    order.setUpdatedAt(Instant.now());
                    orders.save(order);
                }
                inventoryService.confirmReservation(order.getId());
                buyer = order.getBuyer();
            }
        } else if (payment.getCustomOrder() != null) {
            CustomOrder customOrder = payment.getCustomOrder();
            if ("CANCELLED".equals(customOrder.getStatus()) || "EXPIRED".equals(customOrder.getStatus())) {
                log.warn("Yêu cầu in 3D {} đã ở trạng thái {}, giao dịch PayOS {} ghi nhận thanh toán muộn cần đối soát",
                        customOrder.getId(), customOrder.getStatus(), payment.getTransactionId());
                buyer = customOrder.getBuyer();
            } else {
                // Không kéo lùi tiến trình nếu đơn custom đã chuẩn bị hoặc in
                if ("ACCEPTED".equals(customOrder.getStatus()) || "QUOTED".equals(customOrder.getStatus())) {
                    customOrder.setStatus("PAID");
                    customOrder.setUpdatedAt(Instant.now());
                    customs.save(customOrder);
                }
                buyer = customOrder.getBuyer();
            }
        } else if (payment.getCheckoutOrder() != null) {
            CheckoutOrder checkoutOrder = payment.getCheckoutOrder();
            if ("CANCELLED".equals(checkoutOrder.getStatus()) || "EXPIRED".equals(checkoutOrder.getStatus())) {
                log.warn("Đơn checkout {} đã ở trạng thái {}, giao dịch PayOS {} ghi nhận thanh toán muộn cần đối soát",
                        checkoutOrder.getId(), checkoutOrder.getStatus(), payment.getTransactionId());
                buyer = checkoutOrder.getUser();
            } else {
                if ("PENDING_QUOTE".equals(checkoutOrder.getStatus())
                        || "QUOTED".equals(checkoutOrder.getStatus())
                        || "CONFIRMED".equals(checkoutOrder.getStatus())) {
                    checkoutOrder.setStatus("PAID");
                    checkoutOrder.setUpdatedAt(Instant.now());
                    checkoutOrders.save(checkoutOrder);
                }
                buyer = checkoutOrder.getUser();

                List<Order> childOrders = orders.findByCheckoutOrderId(checkoutOrder.getId());
                for (Order o : childOrders) {
                    if (!"CANCELLED".equals(o.getStatus()) && !"EXPIRED".equals(o.getStatus())) {
                        if ("PENDING".equals(o.getStatus())) {
                            o.setStatus("PAID");
                            o.setUpdatedAt(Instant.now());
                            orders.save(o);
                        }
                        inventoryService.confirmReservation(o.getId());
                    }
                }
                List<CustomOrder> childCustoms = customs.findByCheckoutOrderId(checkoutOrder.getId());
                for (CustomOrder c : childCustoms) {
                    if (!"CANCELLED".equals(c.getStatus()) && !"EXPIRED".equals(c.getStatus())) {
                        if (("ACCEPTED".equals(c.getStatus()) || "QUOTED".equals(c.getStatus())) && c.getQuotedPrice() != null) {
                            c.setStatus("PAID");
                            c.setUpdatedAt(Instant.now());
                            customs.save(c);
                        }
                    }
                }
            }
        } else if (payment.getSubscriptionPurchase() != null) {
            SubscriptionPurchase purchase = payment.getSubscriptionPurchase();
            buyer = purchase.getUser();
            if ("CANCELLED".equals(purchase.getStatus()) || "EXPIRED".equals(purchase.getStatus())) {
                log.warn("Giao dịch mua gói {} đã ở trạng thái {}, giao dịch PayOS {} ghi nhận thanh toán muộn cần đối soát",
                        purchase.getId(), purchase.getStatus(), payment.getTransactionId());
            } else {
                userSubscriptionService.activateSubscriptionFromPurchase(purchase);
                notificationMessage = "Gói hội viên " + purchase.getPlan().getName() + " đã được kích hoạt thành công.";
                notificationLink = "/profile";
            }
            // QUAN TRỌNG: Mua gói hội viên bằng tiền TUYỆT ĐỐI KHÔNG CỘNG ĐIỂM THƯỞNG để tránh loop tiền -> điểm -> đổi gói
        } else {
            throw bad("Giao dịch không có thông tin đơn hàng");
        }

        if (subscriptionBenefitService != null) {
            if (payment.getOrder() != null) {
                subscriptionBenefitService.commitAppliedBenefits(
                        SubscriptionBenefitService.UsageTargetType.ORDER, payment.getOrder().getId());
            } else if (payment.getCustomOrder() != null) {
                subscriptionBenefitService.commitAppliedBenefits(
                        SubscriptionBenefitService.UsageTargetType.CUSTOM_ORDER, payment.getCustomOrder().getId());
            } else if (payment.getCheckoutOrder() != null) {
                subscriptionBenefitService.commitAppliedBenefits(
                        SubscriptionBenefitService.UsageTargetType.CHECKOUT_ORDER, payment.getCheckoutOrder().getId());
            }
        }

        payments.save(payment);
        notifications.sendNotification(
                buyer,
                "Đã nhận thanh toán",
                notificationMessage,
                "PAYMENT",
                notificationLink
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
