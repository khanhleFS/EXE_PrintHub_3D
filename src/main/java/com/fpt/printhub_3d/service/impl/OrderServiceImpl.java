package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.OrderErrorCode;
import com.fpt.printhub_3d.dto.order.OrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import com.fpt.printhub_3d.dto.order.RewardCompletionResponseDTO;
import com.fpt.printhub_3d.entity.Enumeration.OrderRewardStatus;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.service.InventoryService;
import com.fpt.printhub_3d.service.NotificationService;
import com.fpt.printhub_3d.service.OrderRewardService;
import com.fpt.printhub_3d.service.OrderService;
import com.fpt.printhub_3d.service.OrderTransitionPolicy;
import com.fpt.printhub_3d.service.PaymentEvidenceService;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final InventoryService inventoryService;
    private final OrderTransitionPolicy orderTransitionPolicy;
    private final OrderCreationService orderCreationService;
    private final OrderMapper orderMapper;

    @Nullable
    private final SubscriptionBenefitService subscriptionBenefitService;
    @Nullable
    private final PaymentEvidenceService paymentEvidenceService;
    @Nullable
    private final OrderRewardService orderRewardService;

    @Override
    @Transactional
    public List<OrderResponseDTO> createOrders(OrderCreateRequestDTO request, User buyer) {
        return orderCreationService.createOrders(request, buyer);
    }

    @Override
    @Transactional
    public RewardCompletionResponseDTO completeRewards(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(OrderErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng ID: " + orderId));

        if (!"COMPLETED".equals(order.getStatus())) {
            throw new ApiException(OrderErrorCode.ORDER_NOT_COMPLETED, "Đơn hàng chưa ở trạng thái COMPLETED");
        }
        if (orderRewardService == null) {
            throw new ApiException(CommonErrorCode.INTERNAL_ERROR, "Dịch vụ xét thưởng xu chưa sẵn sàng");
        }

        PaymentEvidenceService.SourceType targetType = order.getCheckoutOrder() != null
                ? PaymentEvidenceService.SourceType.CHECKOUT_ORDER
                : PaymentEvidenceService.SourceType.ORDER;
        UUID targetId = order.getCheckoutOrder() != null
                ? order.getCheckoutOrder().getId()
                : orderId;
        var reward = orderRewardService.processCompletionReward(targetType, targetId);
        if (reward.status() == OrderRewardStatus.PENDING_PAYMENT_EVIDENCE) {
            throw new ApiException(OrderErrorCode.PAYMENT_REQUIRED, "Đơn hàng chưa hoàn tất thanh toán hợp lệ để nhận điểm thưởng");
        }
        return reward.toLegacyResponse().withOrderId(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getMyOrders(UUID buyerId) {
        return orderMapper.mapOrdersBatch(orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDTO getOrderById(UUID id, User currentUser) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ApiException(OrderErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng"));

        if (currentUser.getRole() != UserRole.ADMIN && !order.getBuyer().getId().equals(currentUser.getId())) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Bạn không có quyền truy cập đơn hàng này");
        }

        return orderMapper.mapOrderById(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getAllOrders() {
        List<Order> orders = orderRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
        return orderMapper.mapOrdersBatch(orders);
    }

    @Override
    @Transactional
    public void updateOrderStatus(UUID id, String nextStatus, User currentUser) {
        updateOrderStatus(id, nextStatus, currentUser, null);
    }

    @Override
    @Transactional
    public void updateOrderStatus(UUID id, String nextStatus, User currentUser, BigDecimal codCollectedAmount) {
        Order order = orderRepository.findLockedById(id)
                .orElseThrow(() -> new ApiException(OrderErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng"));

        String currentStatus = order.getStatus();
        Payment payment = orderMapper.findOrderPayment(order);

        String paymentMethod = order.getPaymentMethod() != null ? order.getPaymentMethod()
                : (order.getCheckoutOrder() != null && order.getCheckoutOrder().getPaymentMethod() != null
                        ? order.getCheckoutOrder().getPaymentMethod()
                        : (payment != null ? payment.getGateway() : "PAYOS"));
        boolean zeroAmountSettlement = order.resolveZeroAmountSettledAt() != null;
        String paymentStatus = zeroAmountSettlement ? "SUCCESS" : payment != null ? payment.getStatus()
                : ("COD".equalsIgnoreCase(paymentMethod) ? "UNPAID" : "PENDING");

        boolean cod = "COD".equalsIgnoreCase(paymentMethod);
        if (codCollectedAmount != null) {
            if (!cod || order.getCheckoutOrder() != null
                    || !"COMPLETED".equals(orderTransitionPolicy.normalizeTargetStatus(nextStatus))) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT,
                        "Xác nhận tiền COD khi hoàn tất đúng cấp đơn thu tiền.");
            }
            if ("COMPLETED".equals(currentStatus)) {
                evaluateCompletionReward(id, codCollectedAmount, currentUser);
                return;
            }
        }

        String resolvedStatus = orderTransitionPolicy.validateAndResolveNextStatus(
                OrderTransitionPolicy.OrderType.MARKETPLACE,
                currentStatus,
                nextStatus,
                paymentMethod,
                paymentStatus,
                currentUser,
                order.getBuyer().getId()
        );

        if ("CANCELLED".equals(resolvedStatus)) {
            if (payment != null && Payment.PENDING_RECONCILIATION.equals(payment.getStatus())) {
                throw new ApiException(CommonErrorCode.CONFLICT, "Giao dịch đang chờ đối soát.");
            }
            if (payment != null && "PAYOS".equalsIgnoreCase(payment.getGateway()) && payment.getTransactionId() != null) {
                payment.setStatus("CANCELLED");
                payment.setUpdatedAt(Instant.now());
                paymentRepository.save(payment);
            }
            inventoryService.releaseActiveReservations(order.getId(), "Hủy đơn hàng PENDING", currentUser.getId().toString());
            if (subscriptionBenefitService != null) {
                subscriptionBenefitService.releaseReservedBenefits(
                        SubscriptionBenefitService.UsageTargetType.ORDER, order.getId());
            }
        } else if ("PREPARING".equals(resolvedStatus) || "PRINTING".equals(resolvedStatus)) {
            inventoryService.confirmReservation(order.getId());
        }

        Instant transitionAt = Instant.now();
        order.setStatus(resolvedStatus);
        order.setUpdatedAt(transitionAt);
        if ("COMPLETED".equals(resolvedStatus) && order.getCompletedAt() == null) {
            order.setCompletedAt(transitionAt);
        }
        orderRepository.save(order);

        if ("COMPLETED".equals(resolvedStatus)) {
            evaluateCompletionReward(id, codCollectedAmount, currentUser);
        }

        notificationService.sendNotification(order.getBuyer(),
                "Cập nhật đơn hàng",
                "Đơn hàng " + id + " đã chuyển sang trạng thái: " + resolvedStatus,
                "ORDER",
                "/orders");
    }

    private void evaluateCompletionReward(UUID orderId, BigDecimal codCollectedAmount, User currentUser) {
        if (orderRewardService != null) {
            if (codCollectedAmount != null) {
                orderRewardService.completeCodAndReward(PaymentEvidenceService.SourceType.ORDER, orderId,
                        codCollectedAmount, currentUser);
            } else {
                orderRewardService.processCompletionReward(PaymentEvidenceService.SourceType.ORDER, orderId);
            }
        } else if (codCollectedAmount != null && paymentEvidenceService != null) {
            paymentEvidenceService.recordCodCollection(PaymentEvidenceService.SourceType.ORDER, orderId,
                    codCollectedAmount, currentUser);
        }
    }
}
