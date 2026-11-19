package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.order.PricingBreakdownDTO;
import com.fpt.printhub_3d.dto.order.PricingBreakdownDTO.PricingStage;
import com.fpt.printhub_3d.dto.subscription.BenefitPreviewResponseDTO;
import com.fpt.printhub_3d.dto.subscription.BenefitPreviewResponseDTO.BenefitStatus;
import com.fpt.printhub_3d.entity.CheckoutOrder;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import com.fpt.printhub_3d.service.SubscriptionBenefitService.BenefitScope;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

final class OrderPricing {
    private OrderPricing() {}

    static BigDecimal checkoutShippingFee(CheckoutOrder checkout, Supplier<BigDecimal> defaultFee) {
        BigDecimal fee = checkout.getShippingFee();
        // Checkout chưa có báo giá từng lưu 0 như phí tạm tính. Khi đã báo giá, 0 là phí đã xác định.
        if (fee == null || (fee.signum() == 0 && "PENDING_QUOTE".equalsIgnoreCase(checkout.getStatus()))) {
            return defaultFee.get();
        }
        return fee;
    }

    static BigDecimal marketplaceSubtotal(List<Order> orders) {
        return orders.stream()
                .filter(order -> !"CANCELLED".equalsIgnoreCase(order.getStatus()))
                .map(order -> order.getSubtotalAmount() != null
                        ? order.getSubtotalAmount() : zeroIfMissing(order.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static PricingBreakdownDTO pendingQuote(Long version, BigDecimal customSubtotal, BigDecimal marketplaceSubtotal,
                                            BigDecimal shippingFee) {
        return new PricingBreakdownDTO(PricingStage.PENDING_QUOTE, version, customSubtotal, marketplaceSubtotal,
                customSubtotal.add(marketplaceSubtotal), null, shippingFee, null, null, null,
                false, null, null, null, null, false);
    }

    static PricingBreakdownDTO estimate(SubscriptionBenefitService benefits, User buyer, BenefitScope scope,
                                       Long version, BigDecimal customSubtotal, BigDecimal marketplaceSubtotal,
                                       BigDecimal shippingFee) {
        BigDecimal itemsSubtotal = customSubtotal.add(marketplaceSubtotal);
        BenefitPreviewResponseDTO preview = benefits != null && buyer != null
                ? benefits.previewBenefits(buyer, scope, itemsSubtotal, shippingFee, customSubtotal, marketplaceSubtotal)
                : null;
        if (preview == null) {
            return new PricingBreakdownDTO(PricingStage.ESTIMATED, version, customSubtotal, marketplaceSubtotal,
                    itemsSubtotal, BigDecimal.ZERO, shippingFee, BigDecimal.ZERO, shippingFee,
                    itemsSubtotal.add(shippingFee), false, null, null, null, null, false);
        }
        return fromPreview(version, customSubtotal, marketplaceSubtotal, preview);
    }

    static PricingBreakdownDTO fromPreview(Long version, BigDecimal customSubtotal, BigDecimal marketplaceSubtotal,
                                          BenefitPreviewResponseDTO preview) {
        return new PricingBreakdownDTO(PricingStage.ESTIMATED, version, customSubtotal, marketplaceSubtotal,
                preview.getItemsSubtotal(), preview.getDiscountAmount(), preview.getBaseShippingFee(),
                preview.getShippingDiscountAmount(), preview.getFinalShippingFee(), preview.getFinalTotal(),
                false, preview.getAppliedSubscriptionId(), preview.getAppliedSubscriptionName(),
                preview.getBenefitStatus(), preview.getIneligibleReason(), preview.isPriorityProduction());
    }

    static PricingBreakdownDTO locked(Long version, BigDecimal customSubtotal, BigDecimal marketplaceSubtotal,
                                     BigDecimal itemsSubtotal, BigDecimal itemDiscount, BigDecimal shippingFee,
                                     BigDecimal shippingDiscount, BigDecimal savedTotal, UUID subscriptionId,
                                     String subscriptionName, Boolean priorityProduction) {
        BigDecimal discount = zeroIfMissing(itemDiscount);
        BigDecimal baseShipping = zeroIfMissing(shippingFee);
        BigDecimal discountShipping = zeroIfMissing(shippingDiscount);
        BigDecimal finalShipping = baseShipping.subtract(discountShipping).max(BigDecimal.ZERO);
        BigDecimal payable = savedTotal != null ? savedTotal
                : totalPayable(itemsSubtotal, discount, baseShipping, discountShipping);
        return new PricingBreakdownDTO(PricingStage.LOCKED, version, customSubtotal, marketplaceSubtotal,
                itemsSubtotal, discount, baseShipping, discountShipping, finalShipping, payable, false,
                subscriptionId, subscriptionName, subscriptionId != null ? BenefitStatus.APPLIED : null,
                null, Boolean.TRUE.equals(priorityProduction));
    }

    static BigDecimal totalPayable(BigDecimal itemsSubtotal, BigDecimal itemDiscount,
                                  BigDecimal shippingFee, BigDecimal shippingDiscount) {
        return itemsSubtotal.subtract(zeroIfMissing(itemDiscount)).max(BigDecimal.ZERO)
                .add(zeroIfMissing(shippingFee).subtract(zeroIfMissing(shippingDiscount)).max(BigDecimal.ZERO));
    }

    private static BigDecimal zeroIfMissing(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
