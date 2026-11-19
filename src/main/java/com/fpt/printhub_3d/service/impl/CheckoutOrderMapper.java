package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderResponseDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderDetailResponseDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderResponseDTO;
import com.fpt.printhub_3d.dto.order.BlockedActionDTO;
import com.fpt.printhub_3d.dto.order.OrderItemResponseDTO;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import com.fpt.printhub_3d.dto.order.PricingBreakdownDTO;
import com.fpt.printhub_3d.dto.payment.PaymentEvidenceResponseDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.OrderRewardService;
import com.fpt.printhub_3d.service.OrderTransitionPolicy;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CheckoutOrderMapper {

    private static final BigDecimal DEFAULT_SHIPPING_FEE = new BigDecimal("30000");

    private final CustomOrderRepository customOrderRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderTransitionPolicy orderTransitionPolicy;

    @Nullable
    private final PaymentRepository paymentRepository;
    @Nullable
    private final StoreSettingRepository storeSettingRepository;
    @Nullable
    private final SubscriptionBenefitService subscriptionBenefitService;
    @Nullable
    private final OrderRewardService orderRewardService;
    @Nullable
    private final CustomOrderReviewRepository customOrderReviewRepository;
    @Nullable
    private final ReviewRepository reviewRepository;
    @Nullable
    private final OrderRewardRecordRepository orderRewardRecordRepository;
    @Nullable
    private final Clock clock;

    public BigDecimal resolveDefaultShippingFee() {
        if (storeSettingRepository != null) {
            try {
                var feeOpt = storeSettingRepository.findById("shippingFee");
                if (feeOpt.isPresent() && feeOpt.get().getValue() != null) {
                    BigDecimal configured = new BigDecimal(feeOpt.get().getValue().trim());
                    if (configured.signum() >= 0) {
                        return configured;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return DEFAULT_SHIPPING_FEE;
    }

    public BigDecimal resolveCustomLineTotal(CustomOrder customOrder) {
        return customOrder != null ? OrderPricing.quotedLineTotal(customOrder) : BigDecimal.ZERO;
    }

    public CheckoutOrderResponseDTO toDTO(CheckoutOrder order, String paymentLinkUrl) {
        List<CustomOrder> customs = customOrderRepository.findByCheckoutOrderId(order.getId());
        List<Order> orders = orderRepository.findByCheckoutOrderId(order.getId());

        Payment payment = paymentRepository != null
                ? paymentRepository.findByCheckoutOrderId(order.getId()).orElse(null)
                : null;
        String paymentMethod = order.getPaymentMethod() != null
                ? order.getPaymentMethod()
                : (payment != null ? payment.getGateway() : "PAYOS");
        boolean zeroAmountSettlement = order.getZeroAmountSettledAt() != null;
        String paymentStatus = zeroAmountSettlement ? "SUCCESS" : payment != null
                ? payment.getStatus()
                : ("PAID".equals(order.getStatus()) ? "SUCCESS" : "PENDING");

        String resolvedOrderType = OrderPricing.checkoutOrderType(order, !customs.isEmpty(), !orders.isEmpty());
        Instant now = (clock != null ? clock : Clock.systemUTC()).instant();
        User currentUser = getCurrentUserSafely();
        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;
        boolean isOwner = currentUser != null && order.getUser() != null && currentUser.getId().equals(order.getUser().getId());

        Map<UUID, CustomOrderReview> customReviewMap = Map.of();
        if (customOrderReviewRepository != null && !customs.isEmpty()) {
            List<UUID> customIds = customs.stream().map(CustomOrder::getId).toList();
            customReviewMap = customOrderReviewRepository.findByCustomOrderIdIn(customIds).stream()
                    .collect(Collectors.toMap(r -> r.getCustomOrder().getId(), r -> r, (r1, r2) -> r1));
        }

        Map<UUID, Map<Long, Review>> itemReviewsByOrderId = new HashMap<>();
        if (reviewRepository != null && !orders.isEmpty()) {
            List<UUID> orderIds = orders.stream().map(Order::getId).toList();
            for (Review r : reviewRepository.findByOrderIdIn(orderIds)) {
                if (r.getOrder() != null && r.getOrderItem() != null) {
                    itemReviewsByOrderId
                            .computeIfAbsent(r.getOrder().getId(), k -> new HashMap<>())
                            .putIfAbsent(r.getOrderItem().getId(), r);
                }
            }
        }

        OrderRewardRecord parentRewardRecord = orderRewardRecordRepository != null
                ? orderRewardRecordRepository.findBySourceTypeAndSourceIdAndRewardType(
                        "CHECKOUT_ORDER", order.getId(), OrderRewardRecord.REWARD_TYPE_COMPLETION).orElse(null)
                : null;

        List<CustomOrder> activeCustoms = customs.stream()
                .filter(c -> !"CANCELLED".equalsIgnoreCase(c.getStatus()))
                .toList();
        boolean allCustomQuoted = !customs.isEmpty()
                ? (!activeCustoms.isEmpty() && activeCustoms.stream().allMatch(c -> resolveCustomLineTotal(c).compareTo(BigDecimal.ZERO) > 0 && !"REQUESTED".equalsIgnoreCase(c.getStatus())))
                : true;
        boolean readyForConfirm = ("PENDING_QUOTE".equals(order.getStatus()) || "QUOTED".equals(order.getStatus())) && allCustomQuoted;

        BigDecimal computedCustomSubtotal = activeCustoms.stream()
                .map(this::resolveCustomLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal computedMarketSubtotal = OrderPricing.marketplaceSubtotal(orders);
        BigDecimal resolvedItemsSubtotal = order.getItemsSubtotal() != null
                ? order.getItemsSubtotal()
                : computedCustomSubtotal.add(computedMarketSubtotal);
        PricingBreakdownDTO pricing = checkoutPricing(order, readyForConfirm, computedCustomSubtotal,
                computedMarketSubtotal, resolvedItemsSubtotal, resolvedOrderType);

        BigDecimal checkoutDiscount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal checkoutNetGoods = (pricing != null && pricing.itemsSubtotal() != null && pricing.itemDiscountAmount() != null)
                ? pricing.itemsSubtotal().subtract(pricing.itemDiscountAmount()).max(BigDecimal.ZERO)
                : resolvedItemsSubtotal.subtract(checkoutDiscount).max(BigDecimal.ZERO);
        int legacyPoints = customs.stream().filter(c -> Boolean.TRUE.equals(c.getRewardProcessed()))
                .mapToInt(c -> c.getRewardPointsEarned() != null ? c.getRewardPointsEarned() : 0).sum()
                + orders.stream().filter(o -> Boolean.TRUE.equals(o.getRewardProcessed()))
                .mapToInt(o -> o.getRewardPointsEarned() != null ? o.getRewardPointsEarned() : 0).sum();
        OrderDisplayMetadata.RewardSnapshot checkoutRewardSnap = OrderDisplayMetadata.computeRewardSnapshot(
                order.getStatus(),
                zeroAmountSettlement,
                checkoutNetGoods,
                parentRewardRecord,
                legacyPoints > 0,
                legacyPoints,
                orderRewardService
        );

        final Map<UUID, CustomOrderReview> finalCustomReviewMap = customReviewMap;
        List<CustomOrderDetailResponseDTO> customDetailDTOs = customs.stream()
                .map(c -> toCustomDetailDTO(c, paymentStatus, payment, zeroAmountSettlement,
                        finalCustomReviewMap.get(c.getId()), parentRewardRecord, now))
                .toList();

        List<CustomOrderResponseDTO> customDTOs = customs.stream().map(c -> CustomOrderResponseDTO.builder()
                .id(c.getId())
                .buyerId(c.getBuyer().getId())
                .buyerName(c.getBuyer().getFullName())
                .requirements(c.getRequirements())
                .attachmentUrl(c.getAttachmentUrl())
                .unitQuotedPrice(c.getUnitQuotedPrice())
                .quotedPrice(c.getQuotedPrice())
                .quoteNote(c.getQuoteNote())
                .quotedAt(c.getQuotedAt())
                .checkoutOrderId(c.getCheckoutOrder() != null ? c.getCheckoutOrder().getId() : null)
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .subtotalAmount(c.getSubtotalAmount())
                .shippingFee(c.getShippingFee())
                .discountAmount(c.getDiscountAmount())
                .shippingDiscountAmount(c.getShippingDiscountAmount())
                .appliedSubscriptionId(c.getAppliedSubscriptionId())
                .appliedSubscriptionName(c.getAppliedSubscriptionName())
                .priorityProduction(c.getPriorityProduction())
                .shippingAddress(c.getShippingAddress())
                .cancelReason(c.getCancelReason())
                .cancelledByRole(c.getCancelledByRole())
                .cancelledAt(c.getCancelledAt())
                .build()).toList();

        List<OrderResponseDTO> orderDTOs = orders.stream().map(o -> {
            List<OrderItem> oItems = orderItemRepository.findByOrderId(o.getId());
            Map<Long, Review> itemReviews = itemReviewsByOrderId.getOrDefault(o.getId(), Map.of());
            boolean orderBuyerOwner = currentUser != null && o.getBuyer() != null && currentUser.getId().equals(o.getBuyer().getId());
            List<OrderItemResponseDTO> itemDTOs = oItems.stream().map(oi -> {
                OrderDisplayMetadata.ItemReviewMeta reviewMeta =
                        OrderDisplayMetadata.computeItemReviewMeta(o, itemReviews.get(oi.getId()), orderBuyerOwner, now);
                return OrderItemResponseDTO.builder()
                        .id(oi.getId())
                        .productId(oi.getProduct() != null ? oi.getProduct().getId() : null)
                        .productTitle(oi.getProduct() != null ? oi.getProduct().getTitle() : "Sản phẩm")
                        .quantity(oi.getQuantity())
                        .unitPrice(oi.getUnitPrice())
                        .subTotal(oi.getUnitPrice() != null ? oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity())) : BigDecimal.ZERO)
                        .color(oi.getColor())
                        .engravingText(oi.getEngravingText())
                        .reviewId(reviewMeta.reviewId())
                        .reviewRating(reviewMeta.reviewRating())
                        .canReview(reviewMeta.canReview())
                        .canEditReview(reviewMeta.canEditReview())
                        .reviewComment(reviewMeta.reviewComment())
                        .reviewCreatedAt(reviewMeta.reviewCreatedAt())
                        .build();
            }).toList();

            OrderDisplayMetadata.RewardSnapshot orderRewardSnap = OrderDisplayMetadata.computeRewardSnapshot(
                    o.getStatus(),
                    zeroAmountSettlement,
                    OrderDisplayMetadata.resolveStandaloneOrderNetGoods(o),
                    parentRewardRecord,
                    o.getRewardProcessed(),
                    o.getRewardPointsEarned(),
                    orderRewardService
            );

            return OrderResponseDTO.builder()
                    .id(o.getId())
                    .buyerId(o.getBuyer() != null ? o.getBuyer().getId() : null)
                    .buyerName(o.getBuyer() != null ? o.getBuyer().getFullName() : "Khách hàng")
                    .sellerId(o.getSeller() != null ? o.getSeller().getId() : null)
                    .sellerName(o.getSeller() != null ? o.getSeller().getFullName() : "PrintHub 3D Platform")
                    .totalAmount(o.getTotalAmount())
                    .commissionFee(o.getCommissionFee())
                    .status(o.getStatus())
                    .paymentStatus(o.resolveZeroAmountSettledAt() != null ? "SUCCESS" : null)
                    .items(itemDTOs)
                    .createdAt(o.getCreatedAt())
                    .updatedAt(o.getUpdatedAt())
                    .subtotalAmount(o.getSubtotalAmount())
                    .shippingFee(o.getShippingFee())
                    .discountAmount(o.getDiscountAmount())
                    .shippingDiscountAmount(o.getShippingDiscountAmount())
                    .appliedSubscriptionId(o.getAppliedSubscriptionId())
                    .appliedSubscriptionName(o.getAppliedSubscriptionName())
                    .priorityProduction(o.getPriorityProduction())
                    .estimatedRewardPoints(orderRewardSnap.estimatedRewardPoints())
                    .rewardStatus(orderRewardSnap.rewardStatus())
                    .build();
        }).toList();

        OrderTransitionPolicy.TransitionEvaluation eval = evaluateParentActions(order, resolvedOrderType, paymentMethod, paymentStatus, isAdmin, isOwner);

        String resolvedLink = paymentLinkUrl;
        if (resolvedLink == null && "CONFIRMED".equals(order.getStatus())
                && payment != null && "PENDING".equals(payment.getStatus())
                && payment.getExpiresAt() != null && payment.getExpiresAt().isAfter(Instant.now())) {
            resolvedLink = payment.getCheckoutUrl();
        }

        return CheckoutOrderResponseDTO.builder()
                .id(order.getId())
                .userId(order.getUser() != null ? order.getUser().getId() : null)
                .buyerName(order.getUser() != null ? order.getUser().getFullName() : null)
                .recipientName(order.getRecipientName() != null ? order.getRecipientName() : (order.getUser() != null ? order.getUser().getFullName() : null))
                .recipientPhone(order.getRecipientPhone() != null ? order.getRecipientPhone() : (order.getUser() != null ? order.getUser().getPhone() : null))
                .orderType(resolvedOrderType)
                .shippingAddress(order.getShippingAddress())
                .shippingMode(order.getShippingMode())
                .status(order.getStatus())
                .itemsSubtotal(resolvedItemsSubtotal)
                .totalAmount(order.getTotalAmount())
                .shippingFee(order.getShippingFee())
                .paymentMethod(paymentMethod)
                .paymentStatus(paymentStatus)
                .cancelReason(order.getCancelReason())
                .summaryVersion(order.getSummaryVersion())
                .itemCount(customs.size() + orders.size())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .customOrders(customDTOs)
                .customOrderDetails(customDetailDTOs)
                .marketplaceOrders(orderDTOs)
                .allowedActions(eval.allowedActions())
                .blockedActions(eval.blockedActions())
                .allCustomQuoted(allCustomQuoted)
                .readyForConfirm(readyForConfirm)
                .paymentLinkUrl(resolvedLink)
                .discountAmount(order.getDiscountAmount())
                .shippingDiscountAmount(order.getShippingDiscountAmount())
                .appliedSubscriptionId(order.getAppliedSubscriptionId())
                .appliedSubscriptionName(order.getAppliedSubscriptionName())
                .priorityProduction(order.getPriorityProduction())
                .cancelledByRole(order.getCancelledByRole())
                .cancelledAt(order.getCancelledAt())
                .pricingBreakdown(pricing)
                .paymentEvidence(PaymentEvidenceResponseDTO.from(payment, order.getZeroAmountSettledAt()))
                .estimatedRewardPoints(checkoutRewardSnap.estimatedRewardPoints())
                .rewardStatus(checkoutRewardSnap.rewardStatus())
                .build();
    }

    public PricingBreakdownDTO checkoutPricing(CheckoutOrder order, boolean readyForConfirm,
                                               BigDecimal customSubtotal, BigDecimal marketSubtotal,
                                               BigDecimal itemsSubtotal, String orderType) {
        String status = order.getStatus();
        if (status == null || "CANCELLED".equalsIgnoreCase(status)) {
            return null;
        }
        Long version = order.getSummaryVersion() != null ? order.getSummaryVersion() : 1L;
        if (Set.of("PENDING_QUOTE", "QUOTED").contains(status)) {
            BigDecimal shippingFee = OrderPricing.checkoutShippingFee(order, this::resolveDefaultShippingFee);
            if (!readyForConfirm) {
                return OrderPricing.pendingQuote(version, customSubtotal, marketSubtotal, shippingFee);
            }
            return OrderPricing.estimateCheckout(subscriptionBenefitService, order, orderType,
                    version, customSubtotal, marketSubtotal, shippingFee);
        }
        return OrderPricing.locked(version, customSubtotal, marketSubtotal, itemsSubtotal, order.getDiscountAmount(),
                order.getShippingFee(), order.getShippingDiscountAmount(), order.getTotalAmount(),
                order.getAppliedSubscriptionId(), order.getAppliedSubscriptionName(), order.getPriorityProduction(),
                order.getZeroAmountSettledAt() != null);
    }

    public CustomOrderDetailResponseDTO toCustomDetailDTO(
            CustomOrder order,
            String parentPaymentStatus,
            Payment parentPayment,
            boolean zeroAmountSettlement,
            CustomOrderReview customReview,
            OrderRewardRecord parentRewardRecord,
            Instant now
    ) {
        String pMethod = order.getPaymentMethod() != null ? order.getPaymentMethod() : "PAYOS";
        Instant zeroAmountSettledAt = order.resolveZeroAmountSettledAt();
        String pStatus = parentPaymentStatus != null && (!zeroAmountSettlement || zeroAmountSettledAt != null)
                ? parentPaymentStatus : "PENDING";
        User currentUser = getCurrentUserSafely();
        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;
        boolean isOwner = currentUser == null || (order.getBuyer() != null && currentUser.getId().equals(order.getBuyer().getId()));
        boolean buyerOwner = currentUser != null && order.getBuyer() != null && currentUser.getId().equals(order.getBuyer().getId());
        OrderTransitionPolicy policy = orderTransitionPolicy != null ? orderTransitionPolicy : new OrderTransitionPolicy();
        OrderTransitionPolicy.TransitionEvaluation eval = policy.evaluateActions(
                OrderTransitionPolicy.OrderType.CUSTOM,
                order.getStatus(),
                pMethod,
                pStatus,
                isAdmin,
                isOwner
        );
        OrderDisplayMetadata.CustomReviewMeta reviewMeta =
                OrderDisplayMetadata.computeCustomReviewMeta(order, customReview, buyerOwner, now);
        OrderDisplayMetadata.RewardSnapshot rewardSnap = OrderDisplayMetadata.computeRewardSnapshot(
                order.getStatus(),
                zeroAmountSettlement || zeroAmountSettledAt != null,
                OrderDisplayMetadata.resolveCustomLineNetGoods(order),
                parentRewardRecord,
                order.getRewardProcessed(),
                order.getRewardPointsEarned(),
                orderRewardService
        );
        return CustomOrderDetailResponseDTO.builder()
                .id(order.getId())
                .buyerId(order.getBuyer() != null ? order.getBuyer().getId() : null)
                .buyerName(order.getBuyer() != null ? order.getBuyer().getFullName() : "Khách hàng")
                .rulerModel(order.getRulerModel())
                .customName(order.getCustomName())
                .customStudentId(order.getCustomStudentId())
                .color(order.getColor())
                .fontStyle(order.getFontStyle())
                .requirements(order.getRequirements())
                .quantity(order.getQuantity() != null ? order.getQuantity() : 1)
                .shippingAddress(order.getShippingAddress())
                .attachmentUrl(order.getAttachmentUrl())
                .unitQuotedPrice(order.getUnitQuotedPrice())
                .quotedPrice(order.getQuotedPrice())
                .quoteNote(order.getQuoteNote())
                .quotedAt(order.getQuotedAt())
                .checkoutOrderId(order.getCheckoutOrder() != null ? order.getCheckoutOrder().getId() : null)
                .status(order.getStatus())
                .paymentMethod(pMethod)
                .paymentStatus(pStatus)
                .allowedActions(eval.allowedActions())
                .blockedActions(eval.blockedActions())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .subtotalAmount(order.getSubtotalAmount())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .shippingDiscountAmount(order.getShippingDiscountAmount())
                .appliedSubscriptionId(order.getAppliedSubscriptionId())
                .appliedSubscriptionName(order.getAppliedSubscriptionName())
                .priorityProduction(order.getPriorityProduction())
                .cancelReason(order.getCancelReason())
                .cancelledByRole(order.getCancelledByRole())
                .cancelledAt(order.getCancelledAt())
                .paymentEvidence(PaymentEvidenceResponseDTO.from(parentPayment, zeroAmountSettledAt))
                .canReview(reviewMeta.canReview())
                .canEditReview(reviewMeta.canEditReview())
                .review(reviewMeta.review())
                .estimatedRewardPoints(rewardSnap.estimatedRewardPoints())
                .rewardStatus(rewardSnap.rewardStatus())
                .build();
    }

    public OrderTransitionPolicy.TransitionEvaluation evaluateParentActions(
            CheckoutOrder order,
            String orderType,
            String paymentMethod,
            String paymentStatus,
            boolean isAdmin,
            boolean isOwner
    ) {
        String st = order.getStatus() != null ? order.getStatus().toUpperCase() : "PENDING_QUOTE";
        if ("MARKETPLACE".equals(orderType)) {
            if (isAdmin && "COD".equalsIgnoreCase(paymentMethod) && Set.of("CONFIRMED", "PROCESSING").contains(st)) {
                return new OrderTransitionPolicy.TransitionEvaluation(List.of("START_PREPARATION", "CANCEL"), List.of());
            }
            OrderTransitionPolicy policy = orderTransitionPolicy != null ? orderTransitionPolicy : new OrderTransitionPolicy();
            return policy.evaluateActions(
                    OrderTransitionPolicy.OrderType.MARKETPLACE,
                    st,
                    paymentMethod,
                    paymentStatus,
                    isAdmin,
                    isOwner
            );
        }
        List<String> allowed = new ArrayList<>();
        List<BlockedActionDTO> blocked = new ArrayList<>();
        boolean isPaid = "PAID".equals(st)
                || Set.of("PREPARING", "PRINTING", "SHIPPING", "COMPLETED").contains(st)
                || "SUCCESS".equalsIgnoreCase(paymentStatus);

        if (isAdmin) {
            if ("PAID".equals(st) || ("CONFIRMED".equals(st) && isPaid)) {
                allowed.add("START_PREPARATION");
            } else if ("PREPARING".equals(st)) {
                allowed.add("START_PRINTING");
            } else if ("PRINTING".equals(st)) {
                allowed.add("START_SHIPPING");
            } else if ("SHIPPING".equals(st)) {
                allowed.add("COMPLETE_DELIVERY");
            } else if ("CONFIRMED".equals(st) && "PAYOS".equalsIgnoreCase(paymentMethod)) {
                blocked.add(new BlockedActionDTO("START_PREPARATION", "PAYMENT_REQUIRED", "Chờ khách hàng hoàn tất thanh toán PayOS"));
            }
            if (Set.of("PENDING_QUOTE", "QUOTED", "CONFIRMED").contains(st) && !isPaid) {
                allowed.add("CANCEL");
            }
        } else if (isOwner && Set.of("PENDING_QUOTE", "QUOTED", "CONFIRMED").contains(st) && !isPaid) {
            allowed.add("CANCEL");
        }
        return new OrderTransitionPolicy.TransitionEvaluation(allowed, blocked);
    }

    public boolean matchesAdminQuery(CheckoutOrder co, String normalizedQ) {
        if (normalizedQ == null) {
            return true;
        }
        String idStr = co.getId() != null ? co.getId().toString().toLowerCase() : "";
        String buyerName = co.getUser() != null && co.getUser().getFullName() != null
                ? co.getUser().getFullName().toLowerCase() : "";
        String recipientName = co.getRecipientName() != null ? co.getRecipientName().toLowerCase() : "";
        String address = co.getShippingAddress() != null ? co.getShippingAddress().toLowerCase() : "";
        if (idStr.contains(normalizedQ) || buyerName.contains(normalizedQ)
                || recipientName.contains(normalizedQ) || address.contains(normalizedQ)) {
            return true;
        }
        return customOrderRepository.findByCheckoutOrderId(co.getId()).stream().anyMatch(c ->
                (c.getId() != null && c.getId().toString().toLowerCase().contains(normalizedQ))
                        || (c.getCustomName() != null && c.getCustomName().toLowerCase().contains(normalizedQ))
                        || (c.getCustomStudentId() != null && c.getCustomStudentId().toLowerCase().contains(normalizedQ))
                        || (c.getRulerModel() != null && c.getRulerModel().toLowerCase().contains(normalizedQ))
        );
    }

    public User getCurrentUserSafely() {
        try {
            return SecurityUtils.getCurrentUser();
        } catch (Exception e) {
            return null;
        }
    }
}
