package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.subscription.BenefitPreviewResponseDTO;
import com.fpt.printhub_3d.entity.SubscriptionPlan;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.entity.UserSubscription;

import java.math.BigDecimal;
import java.util.UUID;

public interface SubscriptionBenefitService {

    enum BenefitScope {
        MARKETPLACE,
        CUSTOM,
        MIXED
    }

    enum UsageTargetType {
        ORDER,
        CUSTOM_ORDER,
        CHECKOUT_ORDER
    }

    record AppliedBenefitSnapshot(
            UUID appliedSubscriptionId,
            String appliedSubscriptionName,
            BigDecimal itemsSubtotal,
            BigDecimal discountAmount,
            BigDecimal baseShippingFee,
            BigDecimal shippingDiscountAmount,
            BigDecimal finalShippingFee,
            BigDecimal finalTotal,
            boolean priorityProduction
    ) {
        public static AppliedBenefitSnapshot empty(BigDecimal itemsSubtotal, BigDecimal baseShippingFee) {
            BigDecimal sub = itemsSubtotal != null ? itemsSubtotal.max(BigDecimal.ZERO) : BigDecimal.ZERO;
            BigDecimal ship = baseShippingFee != null ? baseShippingFee.max(BigDecimal.ZERO) : BigDecimal.ZERO;
            return new AppliedBenefitSnapshot(
                    null,
                    null,
                    sub,
                    BigDecimal.ZERO,
                    ship,
                    BigDecimal.ZERO,
                    ship,
                    sub.add(ship),
                    false
            );
        }
    }

    BenefitPreviewResponseDTO previewBenefits(
            User user,
            BenefitScope scope,
            BigDecimal itemsSubtotal,
            BigDecimal baseShippingFee
    );

    default BenefitPreviewResponseDTO previewBenefits(
            User user,
            BenefitScope scope,
            BigDecimal itemsSubtotal,
            BigDecimal baseShippingFee,
            BigDecimal customSubtotal,
            BigDecimal marketplaceSubtotal
    ) {
        return previewBenefits(user, scope, itemsSubtotal, baseShippingFee);
    }

    AppliedBenefitSnapshot reserveAndApplyBenefits(
            User user,
            UsageTargetType targetType,
            UUID targetId,
            BenefitScope scope,
            BigDecimal itemsSubtotal,
            BigDecimal baseShippingFee
    );

    default AppliedBenefitSnapshot reserveAndApplyBenefits(
            User user,
            UsageTargetType targetType,
            UUID targetId,
            BenefitScope scope,
            BigDecimal itemsSubtotal,
            BigDecimal baseShippingFee,
            BigDecimal customSubtotal,
            BigDecimal marketplaceSubtotal
    ) {
        return reserveAndApplyBenefits(user, targetType, targetId, scope, itemsSubtotal, baseShippingFee);
    }

    void commitAppliedBenefits(UsageTargetType targetType, UUID targetId);

    void releaseReservedBenefits(UsageTargetType targetType, UUID targetId);

    Integer extractFreeShippingUsesLimit(SubscriptionPlan plan);

    default Integer computeRemainingFreeShippingUses(UserSubscription userSubscription) {
        if (userSubscription == null || userSubscription.getPlan() == null) {
            return null;
        }
        Integer limit = extractFreeShippingUsesLimit(userSubscription.getPlan());
        if (limit == null) {
            return null;
        }
        int used = userSubscription.getFreeShippingUsesUsed() != null ? userSubscription.getFreeShippingUsesUsed() : 0;
        return Math.max(0, limit - used);
    }
}

