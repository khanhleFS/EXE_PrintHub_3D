package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CheckoutOrderErrorCode;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.dto.checkout.AdminCheckoutOrderStatusRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderBatchQuoteRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderConfirmRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderResponseDTO;
import com.fpt.printhub_3d.entity.CheckoutOrder;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CheckoutOrderRepository;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.service.CheckoutOrderService;
import com.fpt.printhub_3d.service.InventoryService;
import com.fpt.printhub_3d.service.NotificationService;
import com.fpt.printhub_3d.service.OrderRewardService;
import com.fpt.printhub_3d.service.PaymentEvidenceService;
import com.fpt.printhub_3d.service.PaymentService;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CheckoutOrderServiceImpl implements CheckoutOrderService {

    private final CheckoutOrderRepository checkoutOrderRepository;
    private final OrderRepository orderRepository;
    private final CustomOrderRepository customOrderRepository;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final CheckoutOrderCreationService checkoutOrderCreationService;
    private final CheckoutOrderQuotationService checkoutOrderQuotationService;
    private final CheckoutOrderMapper checkoutOrderMapper;

    @Nullable
    private final PaymentRepository paymentRepository;
    @Nullable
    private final NotificationService notificationService;
    @Nullable
    private final SubscriptionBenefitService subscriptionBenefitService;
    @Nullable
    private final PaymentEvidenceService paymentEvidenceService;
    @Nullable
    private final OrderRewardService orderRewardService;

    @Override
    public CheckoutOrderResponseDTO createCheckoutOrder(User user, CheckoutOrderCreateRequestDTO request) {
        CheckoutOrder checkoutOrder = checkoutOrderCreationService.createCheckoutOrderEntity(user, request);
        return checkoutOrderMapper.toDTO(checkoutOrder, null);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutOrderResponseDTO getCheckoutOrder(User user, UUID id) {
        CheckoutOrder order = checkoutOrderRepository.findById(id)
                .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.CHECKOUT_ORDER_NOT_FOUND));

        if (!order.getUser().getId().equals(user.getId()) && user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return checkoutOrderMapper.toDTO(order, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutOrderResponseDTO> getMyCheckoutOrders(User user) {
        List<CheckoutOrder> orders = checkoutOrderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return orders.stream().map(o -> checkoutOrderMapper.toDTO(o, null)).toList();
    }

    @Override
    public CheckoutOrderResponseDTO confirmCheckoutOrder(User user, UUID id, CheckoutOrderConfirmRequestDTO request) {
        return checkoutOrderQuotationService.confirmCheckoutOrder(user, id, request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutOrderResponseDTO> getAdminCheckoutOrders(String orderType, String status, String query) {
        List<CheckoutOrder> all = (orderType != null && !orderType.isBlank())
                ? checkoutOrderRepository.findByOrderTypeOrderByCreatedAtDesc(orderType.trim().toUpperCase())
                : checkoutOrderRepository.findAllByOrderByCreatedAtDesc();

        String normalizedStatus = status != null && !status.isBlank() ? status.trim().toUpperCase() : null;
        String normalizedQ = query != null && !query.isBlank() ? query.trim().toLowerCase() : null;

        return all.stream()
                .filter(co -> normalizedStatus == null || normalizedStatus.equalsIgnoreCase(co.getStatus()))
                .filter(co -> checkoutOrderMapper.matchesAdminQuery(co, normalizedQ))
                .map(co -> checkoutOrderMapper.toDTO(co, null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutOrderResponseDTO getAdminCheckoutOrder(UUID id) {
        CheckoutOrder co = checkoutOrderRepository.findById(id)
                .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.CHECKOUT_ORDER_NOT_FOUND));
        return checkoutOrderMapper.toDTO(co, null);
    }

    @Override
    public CheckoutOrderResponseDTO batchQuoteCheckoutOrder(User admin, UUID id, CheckoutOrderBatchQuoteRequestDTO request) {
        return checkoutOrderQuotationService.batchQuoteCheckoutOrder(admin, id, request);
    }

    @Override
    public CheckoutOrderResponseDTO updateAdminCheckoutOrderStatus(User admin, UUID id, AdminCheckoutOrderStatusRequestDTO request) {
        if (request == null || request.getEffectiveTarget() == null || request.getEffectiveTarget().isBlank()) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT,
                    "Trạng thái đích không được để trống");
        }

        CheckoutOrder co = checkoutOrderRepository.findLockedById(id)
                .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.CHECKOUT_ORDER_NOT_FOUND));

        List<CustomOrder> customs = customOrderRepository.findByCheckoutOrderId(id);
        List<Order> orders = orderRepository.findByCheckoutOrderId(id);
        Payment payment = paymentRepository != null
                ? paymentRepository.findByCheckoutOrderId(id).orElse(null)
                : null;

        String paymentMethod = co.getPaymentMethod() != null
                ? co.getPaymentMethod()
                : (payment != null ? payment.getGateway() : "PAYOS");
        String paymentStatus = payment != null ? payment.getStatus() : ("PAID".equals(co.getStatus()) ? "SUCCESS" : "PENDING");

        String nextStatus = normalizeCheckoutTarget(request.getEffectiveTarget());
        String current = co.getStatus() != null ? co.getStatus().toUpperCase() : "PENDING_QUOTE";
        BigDecimal codCollectedAmount = request.codCollectedAmount();
        if (codCollectedAmount != null && (!"COD".equalsIgnoreCase(paymentMethod) || !"COMPLETED".equals(nextStatus))) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Chỉ xác nhận thu COD khi hoàn tất đơn COD.");
        }

        if (current.equals(nextStatus)) {
            if (codCollectedAmount != null) {
                evaluateCompletionReward(id, codCollectedAmount, admin);
            }
            return checkoutOrderMapper.toDTO(co, null);
        }

        boolean isPaid = co.getZeroAmountSettledAt() != null || "PAID".equals(current)
                || Set.of("PREPARING", "PRINTING", "SHIPPING", "COMPLETED").contains(current)
                || (payment != null && "SUCCESS".equalsIgnoreCase(paymentStatus));

        if ("CANCELLED".equals(nextStatus)) {
            if (payment != null && Payment.PENDING_RECONCILIATION.equals(payment.getStatus())) {
                throw new ApiException(CommonErrorCode.CONFLICT, "Giao dịch đang chờ đối soát.");
            }
            if (isPaid) {
                throw new ApiException(CheckoutOrderErrorCode.CANNOT_CANCEL_CONFIRMED_ORDER);
            }
            String reason = request.getEffectiveCancelReason();
            if (reason == null || reason.length() < 5) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT,
                        "Vui lòng nhập lý do hủy đơn (tối thiểu 5 ký tự).");
            }
            if (payment != null && "PENDING".equals(payment.getStatus()) && "PAYOS".equals(payment.getGateway())
                    && payment.getTransactionId() != null) {
                paymentService.cancelQuotePayment(payment.getTransactionId(), reason);
            }
            Instant cancelTime = Instant.now();
            co.setStatus("CANCELLED");
            co.setCancelReason(reason);
            co.setCancelledByRole("ADMIN");
            co.setCancelledById(admin != null ? admin.getId() : null);
            co.setCancelledAt(cancelTime);
            co.setUpdatedAt(cancelTime);
            checkoutOrderRepository.save(co);
            if (subscriptionBenefitService != null) {
                subscriptionBenefitService.releaseReservedBenefits(
                        SubscriptionBenefitService.UsageTargetType.CHECKOUT_ORDER, co.getId());
            }

            for (CustomOrder c : customs) {
                if (!"CANCELLED".equalsIgnoreCase(c.getStatus())) {
                    c.setStatus("CANCELLED");
                    c.setCancelReason(reason);
                    c.setCancelledByRole("ADMIN");
                    c.setCancelledById(admin != null ? admin.getId() : null);
                    c.setCancelledAt(cancelTime);
                    c.setUpdatedAt(cancelTime);
                    customOrderRepository.save(c);
                }
            }
            for (Order o : orders) {
                if (!"CANCELLED".equalsIgnoreCase(o.getStatus())) {
                    o.setStatus("CANCELLED");
                    o.setUpdatedAt(cancelTime);
                    orderRepository.save(o);
                    inventoryService.releaseActiveReservations(o.getId(), reason, "CHECKOUT_CANCEL_" + id);
                }
            }
        } else {
            if ("PAYOS".equalsIgnoreCase(paymentMethod) && !isPaid) {
                throw new ApiException(CheckoutOrderErrorCode.CHECKOUT_PAYMENT_REQUIRED,
                        "Đơn hàng PayOS chưa hoàn tất thanh toán, không thể chuyển sang trạng thái " + nextStatus);
            }
            validateParentForwardTransition(current, nextStatus);

            Instant transitionAt = Instant.now();
            co.setStatus(nextStatus);
            co.setUpdatedAt(transitionAt);
            if ("COMPLETED".equals(nextStatus) && co.getCompletedAt() == null) {
                co.setCompletedAt(transitionAt);
            }
            checkoutOrderRepository.save(co);

            for (CustomOrder c : customs) {
                if (!"CANCELLED".equalsIgnoreCase(c.getStatus())) {
                    c.setStatus(nextStatus);
                    c.setUpdatedAt(transitionAt);
                    if ("COMPLETED".equals(nextStatus) && c.getCompletedAt() == null) {
                        c.setCompletedAt(transitionAt);
                    }
                    customOrderRepository.save(c);
                }
            }
            String marketplaceTarget = "PRINTING".equals(nextStatus) ? "PREPARING" : nextStatus;
            for (Order o : orders) {
                if (!"CANCELLED".equalsIgnoreCase(o.getStatus())) {
                    o.setStatus(marketplaceTarget);
                    o.setUpdatedAt(transitionAt);
                    if ("COMPLETED".equals(marketplaceTarget) && o.getCompletedAt() == null) {
                        o.setCompletedAt(transitionAt);
                    }
                    orderRepository.save(o);
                }
            }
            if ("COMPLETED".equals(nextStatus)) {
                evaluateCompletionReward(id, codCollectedAmount, admin);
            }
        }

        if (notificationService != null && co.getUser() != null) {
            if ("CANCELLED".equals(nextStatus)) {
                String shortId = id.toString().length() >= 8 ? id.toString().substring(0, 8) : id.toString();
                notificationService.sendNotification(
                        co.getUser(),
                        "Yêu cầu báo giá đã bị hủy",
                        "Đơn hàng #" + shortId + " đã bị xưởng hủy. Lý do: " + co.getCancelReason(),
                        "CUSTOM_ORDER",
                        "/orders/" + id
                );
            } else {
                notificationService.sendNotification(
                        co.getUser(),
                        "Cập nhật tiến độ đơn hàng",
                        "Đơn hàng #" + id.toString().substring(0, 8) + " đã chuyển sang trạng thái: " + nextStatus,
                        "CUSTOM_ORDER",
                        "/orders"
                );
            }
        }

        return checkoutOrderMapper.toDTO(co, null);
    }

    private void evaluateCompletionReward(UUID checkoutId, BigDecimal collectedAmount, User admin) {
        if (orderRewardService != null) {
            if (collectedAmount == null) {
                orderRewardService.processCompletionReward(PaymentEvidenceService.SourceType.CHECKOUT_ORDER, checkoutId);
            } else {
                orderRewardService.completeCodAndReward(PaymentEvidenceService.SourceType.CHECKOUT_ORDER, checkoutId,
                        collectedAmount, admin);
            }
        } else if (collectedAmount != null && paymentEvidenceService != null) {
            paymentEvidenceService.recordCodCollection(PaymentEvidenceService.SourceType.CHECKOUT_ORDER, checkoutId,
                    collectedAmount, admin);
        }
    }

    private String normalizeCheckoutTarget(String raw) {
        String upper = raw.trim().toUpperCase();
        return switch (upper) {
            case "START_PREPARATION" -> "PREPARING";
            case "START_PRINTING" -> "PRINTING";
            case "START_SHIPPING" -> "SHIPPING";
            case "COMPLETE_DELIVERY" -> "COMPLETED";
            case "CANCEL" -> "CANCELLED";
            default -> upper;
        };
    }

    private void validateParentForwardTransition(String current, String target) {
        boolean valid = switch (current) {
            case "PAID", "CONFIRMED", "PROCESSING" -> Set.of("PREPARING", "PRINTING").contains(target);
            case "PREPARING" -> Set.of("PRINTING", "SHIPPING").contains(target);
            case "PRINTING" -> "SHIPPING".equals(target);
            case "SHIPPING" -> "COMPLETED".equals(target);
            default -> false;
        };
        if (!valid) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT,
                    "Không thể chuyển trạng thái đơn từ " + current + " sang " + target);
        }
    }
}
