package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.order.PricingBreakdownDTO;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.EmailOutboxService;
import com.fpt.printhub_3d.service.EmailOutboxService.PaymentReceiptEmailEvent;
import com.fpt.printhub_3d.service.EmailOutboxService.QuotationEmailEvent;
import com.fpt.printhub_3d.service.EmailOutboxService.QuotationLineItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderEmailEvents {
    private final EmailOutboxService outbox;

    void enqueueQuotation(String sourceType, UUID sourceId, User buyer, PricingBreakdownDTO pricing,
                          List<CustomOrder> customOrders) {
        if (buyer == null || pricing == null || pricing.pricingStage() != PricingBreakdownDTO.PricingStage.ESTIMATED) {
            return;
        }
        outbox.enqueueQuotationIssued(new QuotationEmailEvent(sourceType, sourceId, pricing.quoteVersion(),
                buyer.getEmail(), buyer.getFullName(), pricing.itemsSubtotal(), pricing.itemDiscountAmount(),
                pricing.baseShippingFee(), pricing.shippingDiscountAmount(), pricing.totalPayable(),
                pricing.appliedSubscriptionName(), customOrders.stream().map(this::quotationLine).toList()));
    }

    void enqueueReceipt(Payment payment) {
        if (!payment.isSettledReceiptFor(payment.getAmount(), null)) {
            return;
        }
        if (payment.getCheckoutOrder() != null) {
            var checkout = payment.getCheckoutOrder();
            enqueueReceipt(payment, "CHECKOUT_ORDER", checkout.getId(), checkout.getUser(), null);
        } else if (payment.getCustomOrder() != null) {
            var custom = payment.getCustomOrder();
            enqueueReceipt(payment, "CUSTOM_ORDER", custom.getId(), custom.getBuyer(), null);
        } else if (payment.getOrder() != null) {
            var order = payment.getOrder();
            enqueueReceipt(payment, "ORDER", order.getId(), order.getBuyer(), null);
        } else if (payment.getSubscriptionPurchase() != null) {
            var purchase = payment.getSubscriptionPurchase();
            enqueueReceipt(payment, "SUBSCRIPTION_PURCHASE", purchase.getId(), purchase.getUser(),
                    purchase.getPlanNameSnapshot());
        }
    }

    private void enqueueReceipt(Payment payment, String sourceType, UUID sourceId, User buyer, String planName) {
        if (buyer != null) {
            outbox.enqueuePaymentReceipt(new PaymentReceiptEmailEvent(payment.getId(), sourceType, sourceId,
                    buyer.getEmail(), buyer.getFullName(), payment.getAmount(), payment.getGateway(),
                    payment.getPaidAt(), planName));
        }
    }

    private QuotationLineItem quotationLine(CustomOrder order) {
        int quantity = OrderPricing.quoteQuantity(order);
        BigDecimal total = OrderPricing.quotedLineTotal(order);
        BigDecimal unitPrice = order.getUnitQuotedPrice();
        if (unitPrice == null || unitPrice.signum() <= 0) {
            unitPrice = total.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
        }
        String model = order.getRulerModel();
        return new QuotationLineItem(model == null || model.isBlank() ? "Sản phẩm in 3D" : model,
                quantity, unitPrice, total);
    }
}
