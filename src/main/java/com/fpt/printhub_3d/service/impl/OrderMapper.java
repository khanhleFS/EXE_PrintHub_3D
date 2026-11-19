package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.security.CustomUserDetail;
import com.fpt.printhub_3d.dto.order.OrderItemResponseDTO;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import com.fpt.printhub_3d.dto.order.ShippingInfoResponseDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.OrderRewardService;
import com.fpt.printhub_3d.service.OrderTransitionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final ShippingInfoRepository shippingInfoRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ReviewRepository reviewRepository;
    private final OrderTransitionPolicy orderTransitionPolicy;

    @Nullable
    private final OrderRewardService orderRewardService;
    @Nullable
    private final OrderRewardRecordRepository orderRewardRecordRepository;
    @Nullable
    private final Clock clock;

    public OrderResponseDTO mapOrderById(Order order) {
        ShippingInfo shippingInfo = shippingInfoRepository.findById(order.getId()).orElse(null);
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        return mapOrderToDTO(order, shippingInfo, items);
    }

    public OrderResponseDTO mapOrderToDTO(Order order, ShippingInfo shippingInfo, List<OrderItem> items) {
        List<Review> reviews = reviewRepository.findByOrderId(order.getId());
        OrderRewardRecord rewardRecord = resolveSingleOrderRewardRecord(order);
        return mapOrderToDTO(order, shippingInfo, items, reviews, rewardRecord);
    }

    public OrderResponseDTO mapOrderToDTO(
            Order order,
            ShippingInfo shippingInfo,
            List<OrderItem> items,
            List<Review> orderReviews,
            OrderRewardRecord rewardRecord
    ) {
        Payment payment = findOrderPayment(order);

        String pMethod = order.getPaymentMethod() != null ? order.getPaymentMethod()
                : (order.getCheckoutOrder() != null && order.getCheckoutOrder().getPaymentMethod() != null
                        ? order.getCheckoutOrder().getPaymentMethod()
                        : (payment != null ? payment.getGateway() : "PAYOS"));
        String pStatus = order.resolveZeroAmountSettledAt() != null ? "SUCCESS"
                : payment != null ? payment.getStatus() : ("COD".equalsIgnoreCase(pMethod) ? "UNPAID" : "PENDING");

        User current = getCurrentUserSafely();
        boolean isAdmin = current != null && current.getRole() == UserRole.ADMIN;
        boolean isOwner = current != null && order.getBuyer() != null && current.getId().equals(order.getBuyer().getId());
        if (current == null) {
            isAdmin = true;
        }

        OrderTransitionPolicy.TransitionEvaluation eval = orderTransitionPolicy.evaluateActions(
                OrderTransitionPolicy.OrderType.MARKETPLACE,
                order.getStatus(),
                pMethod,
                pStatus,
                isAdmin,
                isOwner
        );

        ShippingInfoResponseDTO shippingDTO = null;
        if (shippingInfo != null) {
            shippingDTO = ShippingInfoResponseDTO.builder()
                    .recipientName(shippingInfo.getRecipientName())
                    .phone(shippingInfo.getPhone())
                    .address(shippingInfo.getAddress())
                    .province(shippingInfo.getProvince())
                    .trackingNumber(shippingInfo.getTrackingNumber())
                    .build();
        }

        Instant now = (clock != null ? clock : Clock.systemUTC()).instant();
        List<Review> reviews = orderReviews != null ? orderReviews : List.of();
        Map<Long, Review> reviewByItem = reviews.stream()
                .filter(r -> r.getOrderItem() != null)
                .collect(Collectors.toMap(r -> r.getOrderItem().getId(), r -> r, (r1, r2) -> r1));

        List<OrderItemResponseDTO> itemDTOs = items != null ? items.stream()
                .map(item -> {
                    Review itemReview = reviewByItem.get(item.getId());
                    OrderDisplayMetadata.ItemReviewMeta reviewMeta =
                            OrderDisplayMetadata.computeItemReviewMeta(order, itemReview, isOwner, now);
                    return OrderItemResponseDTO.builder()
                            .id(item.getId())
                            .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                            .productTitle(item.getProduct() != null ? item.getProduct().getTitle() : "Sản phẩm")
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO)
                            .subTotal(item.getUnitPrice() != null ? item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())) : BigDecimal.ZERO)
                            .color(item.getColor())
                            .engravingText(item.getEngravingText())
                            .reviewId(reviewMeta.reviewId())
                            .reviewRating(reviewMeta.reviewRating())
                            .canReview(reviewMeta.canReview())
                            .canEditReview(reviewMeta.canEditReview())
                            .reviewComment(reviewMeta.reviewComment())
                            .reviewCreatedAt(reviewMeta.reviewCreatedAt())
                            .build();
                })
                .toList() : List.of();

        OrderDisplayMetadata.RewardSnapshot rewardSnap = OrderDisplayMetadata.computeRewardSnapshot(
                order.getStatus(),
                order.resolveZeroAmountSettledAt() != null,
                OrderDisplayMetadata.resolveStandaloneOrderNetGoods(order),
                rewardRecord,
                order.getRewardProcessed(),
                order.getRewardPointsEarned(),
                orderRewardService
        );

        return OrderResponseDTO.builder()
                .id(order.getId())
                .buyerId(order.getBuyer() != null ? order.getBuyer().getId() : null)
                .buyerName(order.getBuyer() != null ? order.getBuyer().getFullName() : null)
                .sellerId(order.getSeller() != null ? order.getSeller().getId() : null)
                .sellerName(order.getSeller() != null ? order.getSeller().getFullName() : null)
                .totalAmount(order.getTotalAmount())
                .commissionFee(order.getCommissionFee())
                .status(order.getStatus())
                .paymentMethod(pMethod)
                .paymentStatus(pStatus)
                .orderCode(payment != null ? payment.getTransactionId() : null)
                .shippingInfo(shippingDTO)
                .items(itemDTOs)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .paymentDueAt(order.getPaymentDueAt())
                .allowedActions(eval.allowedActions())
                .blockedActions(eval.blockedActions())
                .subtotalAmount(order.getSubtotalAmount())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .shippingDiscountAmount(order.getShippingDiscountAmount())
                .appliedSubscriptionId(order.getAppliedSubscriptionId())
                .appliedSubscriptionName(order.getAppliedSubscriptionName())
                .priorityProduction(order.getPriorityProduction())
                .estimatedRewardPoints(rewardSnap.estimatedRewardPoints())
                .rewardStatus(rewardSnap.rewardStatus())
                .build();
    }

    public List<OrderResponseDTO> mapOrdersBatch(List<Order> orders) {
        if (orders.isEmpty()) return List.of();
        List<UUID> orderIds = orders.stream().map(Order::getId).toList();
        Map<UUID, ShippingInfo> shippingMap = shippingInfoRepository.findByIdIn(orderIds).stream()
                .collect(Collectors.toMap(ShippingInfo::getId, s -> s));
        Map<UUID, List<OrderItem>> itemsMap = orderItemRepository.findByOrderIn(orders).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));
        Map<UUID, List<Review>> reviewsMap = reviewRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(r -> r.getOrder().getId()));

        Map<UUID, OrderRewardRecord> standaloneRewards = new HashMap<>();
        Map<UUID, OrderRewardRecord> parentRewards = new HashMap<>();
        if (orderRewardRecordRepository != null) {
            List<UUID> standaloneIds = orders.stream()
                    .filter(o -> o.getCheckoutOrder() == null).map(Order::getId).toList();
            if (!standaloneIds.isEmpty()) {
                orderRewardRecordRepository.findBySourceTypeAndSourceIdInAndRewardType(
                                "ORDER", standaloneIds, OrderRewardRecord.REWARD_TYPE_COMPLETION)
                        .forEach(r -> standaloneRewards.put(r.getSourceId(), r));
            }
            List<UUID> checkoutIds = orders.stream()
                    .filter(o -> o.getCheckoutOrder() != null)
                    .map(o -> o.getCheckoutOrder().getId()).distinct().toList();
            if (!checkoutIds.isEmpty()) {
                orderRewardRecordRepository.findBySourceTypeAndSourceIdInAndRewardType(
                                "CHECKOUT_ORDER", checkoutIds, OrderRewardRecord.REWARD_TYPE_COMPLETION)
                        .forEach(r -> parentRewards.put(r.getSourceId(), r));
            }
        }

        return orders.stream().map(o -> {
            OrderRewardRecord rec = o.getCheckoutOrder() != null
                    ? parentRewards.get(o.getCheckoutOrder().getId())
                    : standaloneRewards.get(o.getId());
            return mapOrderToDTO(o, shippingMap.get(o.getId()),
                    itemsMap.getOrDefault(o.getId(), List.of()),
                    reviewsMap.getOrDefault(o.getId(), List.of()),
                    rec);
        }).toList();
    }

    public Payment findOrderPayment(Order order) {
        return order.getCheckoutOrder() != null
                ? paymentRepository.findByCheckoutOrderId(order.getCheckoutOrder().getId()).orElse(null)
                : paymentRepository.findByOrderId(order.getId()).orElse(null);
    }

    private OrderRewardRecord resolveSingleOrderRewardRecord(Order order) {
        if (orderRewardRecordRepository == null || order == null) {
            return null;
        }
        String srcType = order.getCheckoutOrder() != null ? "CHECKOUT_ORDER" : "ORDER";
        UUID srcId = order.getCheckoutOrder() != null ? order.getCheckoutOrder().getId() : order.getId();
        return orderRewardRecordRepository.findBySourceTypeAndSourceIdAndRewardType(
                srcType, srcId, OrderRewardRecord.REWARD_TYPE_COMPLETION).orElse(null);
    }

    private User getCurrentUserSafely() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetail userDetail) {
                return userDetail.getUser();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
