package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.security.CustomUserDetail;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderDetailResponseDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderResponseDTO;
import com.fpt.printhub_3d.dto.order.PricingBreakdownDTO;
import com.fpt.printhub_3d.dto.payment.PaymentEvidenceResponseDTO;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.CustomOrderReview;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.OrderRewardRecord;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CustomOrderReviewRepository;
import com.fpt.printhub_3d.repository.OrderRewardRecordRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.repository.StoreSettingRepository;
import com.fpt.printhub_3d.service.OrderRewardService;
import com.fpt.printhub_3d.service.OrderTransitionPolicy;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CustomOrderMapper {

    static final BigDecimal DEFAULT_SHIPPING_FEE = new BigDecimal("30000");

    private final PaymentRepository paymentRepository;
    private final @Nullable StoreSettingRepository storeSettingRepository;
    private final OrderTransitionPolicy orderTransitionPolicy;
    private final @Nullable SubscriptionBenefitService subscriptionBenefitService;
    private final @Nullable CustomOrderReviewRepository customOrderReviewRepository;
    private final @Nullable OrderRewardRecordRepository orderRewardRecordRepository;
    private final @Nullable OrderRewardService orderRewardService;
    private final @Nullable Clock clock;

    static BigDecimal resolveLineTotal(CustomOrder order) {
        return order != null ? OrderPricing.quotedLineTotal(order) : BigDecimal.ZERO;
    }

    static BigDecimal resolveDefaultShippingFee(StoreSettingRepository storeSettingRepository) {
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

    BigDecimal resolveDefaultShippingFee() {
        return resolveDefaultShippingFee(storeSettingRepository);
    }

    Payment findOrderPayment(CustomOrder order) {
        if (paymentRepository == null || order == null) {
            return null;
        }
        return order.getCheckoutOrder() != null
                ? paymentRepository.findByCheckoutOrderId(order.getCheckoutOrder().getId()).orElse(null)
                : paymentRepository.findByCustomOrderId(order.getId()).orElse(null);
    }

    CustomOrderResponseDTO toRequestResponseDTO(CustomOrder saved) {
        return CustomOrderResponseDTO.builder()
                .id(saved.getId())
                .buyerId(saved.getBuyer().getId())
                .buyerName(saved.getBuyer().getFullName())
                .processedById(saved.getProcessedBy() != null ? saved.getProcessedBy().getId() : null)
                .processedByName(saved.getProcessedBy() != null ? saved.getProcessedBy().getFullName() : null)
                .requirements(saved.getRequirements())
                .attachmentUrl(saved.getAttachmentUrl())
                .quotedPrice(saved.getQuotedPrice())
                .status(saved.getStatus())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .shippingAddress(saved.getShippingAddress())
                .cancelReason(saved.getCancelReason())
                .cancelledByRole(saved.getCancelledByRole())
                .cancelledAt(saved.getCancelledAt())
                .build();
    }

    CustomOrderDetailResponseDTO toDTO(CustomOrder o) {
        CustomOrderReview customReview = customOrderReviewRepository != null
                ? customOrderReviewRepository.findByCustomOrderId(o.getId()).orElse(null)
                : null;
        OrderRewardRecord rewardRecord = null;
        if (orderRewardRecordRepository != null) {
            String srcType = o.getCheckoutOrder() != null ? "CHECKOUT_ORDER" : "CUSTOM_ORDER";
            UUID srcId = o.getCheckoutOrder() != null ? o.getCheckoutOrder().getId() : o.getId();
            rewardRecord = orderRewardRecordRepository.findBySourceTypeAndSourceIdAndRewardType(
                    srcType, srcId, OrderRewardRecord.REWARD_TYPE_COMPLETION).orElse(null);
        }
        return toDTO(o, customReview, rewardRecord);
    }

    List<CustomOrderDetailResponseDTO> toDTOList(List<CustomOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        List<UUID> customIds = orders.stream().map(CustomOrder::getId).toList();
        Map<UUID, CustomOrderReview> reviewMap = customOrderReviewRepository != null
                ? customOrderReviewRepository.findByCustomOrderIdIn(customIds).stream()
                        .collect(Collectors.toMap(r -> r.getCustomOrder().getId(), r -> r, (r1, r2) -> r1))
                : Map.of();

        Map<UUID, OrderRewardRecord> standaloneRewards = new HashMap<>();
        Map<UUID, OrderRewardRecord> parentRewards = new HashMap<>();
        if (orderRewardRecordRepository != null) {
            List<UUID> standaloneIds = orders.stream()
                    .filter(c -> c.getCheckoutOrder() == null).map(CustomOrder::getId).toList();
            if (!standaloneIds.isEmpty()) {
                orderRewardRecordRepository.findBySourceTypeAndSourceIdInAndRewardType(
                        "CUSTOM_ORDER", standaloneIds, OrderRewardRecord.REWARD_TYPE_COMPLETION)
                        .forEach(r -> standaloneRewards.put(r.getSourceId(), r));
            }
            List<UUID> checkoutIds = orders.stream()
                    .filter(c -> c.getCheckoutOrder() != null)
                    .map(c -> c.getCheckoutOrder().getId()).distinct().toList();
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
            return toDTO(o, reviewMap.get(o.getId()), rec);
        }).toList();
    }

    PricingBreakdownDTO standalonePricing(CustomOrder order) {
        String status = order.getStatus();
        if (order.getCheckoutOrder() != null || status == null || "CANCELLED".equalsIgnoreCase(status)) {
            return null;
        }
        BigDecimal lineTotal = resolveLineTotal(order);
        if (Set.of("REQUESTED", "QUOTED").contains(status)) {
            BigDecimal shippingFee = order.getShippingFee() != null ? order.getShippingFee() : resolveDefaultShippingFee();
            if ("REQUESTED".equals(status) || lineTotal.signum() <= 0) {
                return OrderPricing.pendingQuote(order.getQuoteVersion(), lineTotal, BigDecimal.ZERO, shippingFee);
            }
            return OrderPricing.estimate(subscriptionBenefitService, order.getBuyer(),
                    SubscriptionBenefitService.BenefitScope.CUSTOM, order.getQuoteVersion(),
                    lineTotal, BigDecimal.ZERO, shippingFee);
        }
        BigDecimal subtotal = order.getSubtotalAmount() != null ? order.getSubtotalAmount() : lineTotal;
        return OrderPricing.locked(order.getQuoteVersion(), subtotal, BigDecimal.ZERO, subtotal,
                order.getDiscountAmount(), order.getShippingFee(), order.getShippingDiscountAmount(), null,
                order.getAppliedSubscriptionId(), order.getAppliedSubscriptionName(), order.getPriorityProduction(),
                order.getZeroAmountSettledAt() != null);
    }

    private CustomOrderDetailResponseDTO toDTO(
            CustomOrder o,
            CustomOrderReview customReview,
            OrderRewardRecord rewardRecord
    ) {
        Payment p = findOrderPayment(o);

        String pMethod = o.getPaymentMethod() != null ? o.getPaymentMethod() : (p != null ? p.getGateway() : "PAYOS");
        Instant zeroAmountSettledAt = o.resolveZeroAmountSettledAt();
        String pStatus = zeroAmountSettledAt != null ? "SUCCESS" : p != null ? p.getStatus() : "PENDING";

        User current = getCurrentUserSafely();
        boolean isAdmin = current != null && current.getRole() == UserRole.ADMIN;
        boolean isOwner = current != null && o.getBuyer() != null && current.getId().equals(o.getBuyer().getId());
        if (current == null) {
            isAdmin = true;
        }

        OrderTransitionPolicy.TransitionEvaluation eval = orderTransitionPolicy.evaluateActions(
                OrderTransitionPolicy.OrderType.CUSTOM,
                o.getStatus(),
                pMethod,
                pStatus,
                isAdmin,
                isOwner
        );

        Instant now = (clock != null ? clock : Clock.systemUTC()).instant();
        OrderDisplayMetadata.CustomReviewMeta reviewMeta =
                OrderDisplayMetadata.computeCustomReviewMeta(o, customReview, isOwner, now);

        PricingBreakdownDTO pricing = standalonePricing(o);
        BigDecimal eligibleNet = (pricing != null && pricing.itemsSubtotal() != null && pricing.itemDiscountAmount() != null)
                ? pricing.itemsSubtotal().subtract(pricing.itemDiscountAmount()).max(BigDecimal.ZERO)
                : OrderDisplayMetadata.resolveCustomLineNetGoods(o);
        OrderDisplayMetadata.RewardSnapshot rewardSnap = OrderDisplayMetadata.computeRewardSnapshot(
                o.getStatus(),
                zeroAmountSettledAt != null,
                eligibleNet,
                rewardRecord,
                o.getRewardProcessed(),
                o.getRewardPointsEarned(),
                orderRewardService
        );

        return CustomOrderDetailResponseDTO.builder()
                .id(o.getId())
                .buyerId(o.getBuyer().getId())
                .buyerName(o.getBuyer().getFullName())
                .processedById(o.getProcessedBy() != null ? o.getProcessedBy().getId() : null)
                .processedByName(o.getProcessedBy() != null ? o.getProcessedBy().getFullName() : null)
                .requirements(o.getRequirements())
                .quantity(o.getQuantity() != null ? o.getQuantity() : 1)
                .shippingAddress(o.getShippingAddress())
                .attachmentUrl(o.getAttachmentUrl())
                .unitQuotedPrice(o.getUnitQuotedPrice())
                .quotedPrice(o.getQuotedPrice())
                .quoteNote(o.getQuoteNote())
                .quotedAt(o.getQuotedAt())
                .checkoutOrderId(o.getCheckoutOrder() != null ? o.getCheckoutOrder().getId() : null)
                .status(o.getStatus())
                .rulerModel(o.getRulerModel())
                .customName(o.getCustomName())
                .customStudentId(o.getCustomStudentId())
                .color(o.getColor())
                .fontStyle(o.getFontStyle())
                .paymentMethod(pMethod)
                .paymentStatus(pStatus)
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .allowedActions(eval.allowedActions())
                .blockedActions(eval.blockedActions())
                .subtotalAmount(o.getSubtotalAmount())
                .shippingFee(o.getShippingFee())
                .discountAmount(o.getDiscountAmount())
                .shippingDiscountAmount(o.getShippingDiscountAmount())
                .appliedSubscriptionId(o.getAppliedSubscriptionId())
                .appliedSubscriptionName(o.getAppliedSubscriptionName())
                .priorityProduction(o.getPriorityProduction())
                .cancelReason(o.getCancelReason())
                .cancelledByRole(o.getCancelledByRole())
                .cancelledAt(o.getCancelledAt())
                .quoteVersion(o.getCheckoutOrder() == null ? o.getQuoteVersion() : null)
                .pricingBreakdown(pricing)
                .paymentEvidence(PaymentEvidenceResponseDTO.from(p, zeroAmountSettledAt))
                .canReview(reviewMeta.canReview())
                .canEditReview(reviewMeta.canEditReview())
                .review(reviewMeta.review())
                .estimatedRewardPoints(rewardSnap.estimatedRewardPoints())
                .rewardStatus(rewardSnap.rewardStatus())
                .build();
    }

    private User getCurrentUserSafely() {
        try {
            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null
                    && authentication.getPrincipal() instanceof CustomUserDetail userDetail) {
                return userDetail.getUser();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
