package com.fpt.printhub_3d.dto.order;

import com.fpt.printhub_3d.dto.subscription.BenefitPreviewResponseDTO.BenefitStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PricingBreakdownDTO(
        PricingStage pricingStage,
        Long quoteVersion,
        BigDecimal customItemsSubtotal,
        BigDecimal marketplaceItemsSubtotal,
        BigDecimal itemsSubtotal,
        BigDecimal itemDiscountAmount,
        BigDecimal baseShippingFee,
        BigDecimal shippingDiscountAmount,
        BigDecimal finalShippingFee,
        BigDecimal totalPayable,
        boolean zeroAmountSettlement,
        UUID appliedSubscriptionId,
        String appliedSubscriptionName,
        BenefitStatus benefitStatus,
        String ineligibleReason,
        boolean priorityProduction
) {
    public enum PricingStage {
        PENDING_QUOTE, ESTIMATED, LOCKED
    }
}
