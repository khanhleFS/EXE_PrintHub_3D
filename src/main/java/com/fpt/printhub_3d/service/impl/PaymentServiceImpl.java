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
import com.fpt.printhub_3d.service.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orders;
    private final CustomOrderRepository customs;
    private final CheckoutOrderRepository checkoutOrders;
    private final PaymentRepository payments;
    private final SubscriptionPurchaseRepository subscriptionPurchases;
    private final UserSubscriptionService userSubscriptionService;
    private final PayOS payOS;
    private final NotificationService notifications;
    private final InventoryService inventoryService;

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

        } else if ("CHECKOUT_ORDER".equals(request.orderType())) {
            checkoutOrder = checkoutOrders.findLockedById(request.orderId())
                    .orElseThrow(() -> bad("Không tìm thấy đơn thanh toán"));
            if (!checkoutOrder.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            if (!"CONFIRMED".equals(checkoutOrder.getStatus())) {
                throw bad("Đơn không ở trạng thái có thể thanh toán");
            }
            List<CustomOrder> customsList = customs.findByCheckoutOrderId(checkoutOrder.getId());
            boolean hasUnacceptedCustom = customsList.stream()
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
                ? request.returnUrl() : (returnUrl != null ? returnUrl : "http://localhost:5173/payment-result");
        String finalCancelUrl = (request.cancelUrl() != null && !request.cancelUrl().isBlank())
                ? request.cancelUrl() : (cancelUrl != null ? cancelUrl : "http://localhost:5173/payment-result?cancel=true");

        String description = (request.description() != null && !request.description().isBlank())
                ? request.description()
                : (subscriptionPurchase != null ? "SUB " + code : "PrintHub " + code);
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
        payment.setCheckoutOrder(checkoutOrder);
        payment.setSubscriptionPurchase(subscriptionPurchase);
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
        } else if (payment.getCheckoutOrder() != null) {
            checkoutOrders.findLockedById(payment.getCheckoutOrder().getId()).orElseThrow();
        } else if (payment.getSubscriptionPurchase() != null) {
            subscriptionPurchases.findLockedById(payment.getSubscriptionPurchase().getId()).orElseThrow();
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
                if ("PENDING_QUOTE".equals(checkoutOrder.getStatus()) || "CONFIRMED".equals(checkoutOrder.getStatus())) {
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
