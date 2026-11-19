package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CheckoutOrderErrorCode;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.CustomPrintErrorCode;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderQuoteRequestDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderStatusRequestDTO;
import com.fpt.printhub_3d.dto.order.PricingBreakdownDTO;
import com.fpt.printhub_3d.entity.CheckoutOrder;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CheckoutOrderRepository;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.repository.StoreSettingRepository;
import com.fpt.printhub_3d.service.NotificationService;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomOrderQuotationService {

    private final CustomOrderRepository customOrderRepository;
    private final @Nullable CheckoutOrderRepository checkoutOrderRepository;
    private final @Nullable OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final @Nullable StoreSettingRepository storeSettingRepository;
    private final @Nullable SubscriptionBenefitService subscriptionBenefitService;
    private final @Nullable NotificationService notificationService;
    private final @Nullable OrderEmailEvents emailEvents;
    private final CustomOrderMapper customOrderMapper;

    void quoteCustomOrder(UUID id, CustomOrderQuoteRequestDTO request, User admin) {
        CheckoutOrder parent = checkoutOrderRepository != null
                ? customOrderRepository.findCheckoutId(id)
                        .flatMap(checkoutOrderRepository::findLockedById).orElse(null)
                : null;
        CustomOrder order = customOrderRepository.findLockedById(id)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu in"));

        if (!Set.of("REQUESTED", "QUOTED").contains(order.getStatus())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Không thể sửa báo giá của yêu cầu đã được chấp nhận");
        }

        BigDecimal unitPrice = request != null ? request.getEffectiveUnitPrice() : null;
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Đơn giá sản phẩm phải lớn hơn 0 VND");
        }

        if (parent != null && !Set.of("PENDING_QUOTE", "QUOTED").contains(parent.getStatus())) {
            throw new ApiException(
                    CheckoutOrderErrorCode.INVALID_ORDER_TRANSITION,
                    "Không thể báo giá lại khi đơn cha đã chuyển sang trạng thái " + parent.getStatus()
            );
        }

        int qty = OrderPricing.quoteQuantity(order);
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
        String normalizedNote = request.note() != null && !request.note().isBlank() ? request.note().trim() : null;
        if (!OrderPricing.quoteChanged(order, unitPrice, lineTotal, normalizedNote)) {
            return;
        }
        Instant now = Instant.now();

        if (order.getCheckoutOrder() == null) {
            order.setQuoteVersion(order.getQuoteVersion() + 1);
            if (order.getShippingFee() == null) {
                order.setShippingFee(CustomOrderMapper.resolveDefaultShippingFee(storeSettingRepository));
            }
        }
        order.setUnitQuotedPrice(unitPrice);
        order.setQuotedPrice(lineTotal);
        order.setQuoteNote(normalizedNote);
        order.setQuotedAt(now);
        order.setProcessedBy(admin);
        order.setStatus("QUOTED");
        order.setUpdatedAt(now);
        customOrderRepository.save(order);

        if (parent != null) {
            syncParentCheckoutAfterQuote(id, order, parent, now);
        } else if (order.getCheckoutOrder() == null && emailEvents != null) {
            emailEvents.enqueueQuotation(
                    "CUSTOM_ORDER", id, order.getBuyer(), customOrderMapper.standalonePricing(order), List.of(order));
        }

        if (notificationService != null && order.getBuyer() != null) {
            String targetLink = parent != null ? "/orders/" + parent.getId() : "/quotations";
            notificationService.sendNotification(order.getBuyer(),
                    "Đã có báo giá yêu cầu in",
                    "Yêu cầu in 3D " + id + " đã được báo giá: " + unitPrice + " VND/chiếc (SL " + qty + ", thành tiền " + lineTotal + " VND).",
                    "CUSTOM_ORDER",
                    targetLink);
        }
    }

    void validateAcceptRequestBeforeTransition(CustomOrder order, CustomOrderStatusRequestDTO request, User user) {
        if (user == null || !order.getBuyer().getId().equals(user.getId())) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Chỉ người đặt mới có quyền duyệt báo giá");
        }
        if (order.getCheckoutOrder() != null) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Vui lòng xác nhận báo giá trên đơn thanh toán.");
        }
        if (Set.of("QUOTED", "ACCEPTED", "PAID").contains(order.getStatus())
                && !order.getQuoteVersion().equals(request.quoteVersion())) {
            throw customPricingConflict(order, CustomPrintErrorCode.CONFIRMATION_VERSION_CONFLICT,
                    customOrderMapper.standalonePricing(order));
        }
    }

    boolean isAlreadyConfirmedWithSamePrice(CustomOrder order, CustomOrderStatusRequestDTO request) {
        if ("ACCEPTED".equals(order.getStatus()) || "PAID".equals(order.getStatus())) {
            PricingBreakdownDTO pricing = customOrderMapper.standalonePricing(order);
            if (request.expectedTotalPayable() != null
                    && request.expectedTotalPayable().compareTo(pricing.totalPayable()) != 0) {
                throw customPricingConflict(order, CustomPrintErrorCode.CONFIRMATION_PRICE_CHANGED, pricing);
            }
            return true;
        }
        return false;
    }

    void confirmCustomQuote(CustomOrder order, CustomOrderStatusRequestDTO request) {
        if (!"PAYOS".equalsIgnoreCase(request.paymentMethod())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Đơn in 3D chỉ hỗ trợ thanh toán trực tuyến PayOS.");
        }
        BigDecimal subtotal = CustomOrderMapper.resolveLineTotal(order);
        if (subtotal.signum() <= 0) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Báo giá chưa có giá trị hợp lệ.");
        }
        BigDecimal shippingFee = order.getShippingFee() != null
                ? order.getShippingFee()
                : CustomOrderMapper.resolveDefaultShippingFee(storeSettingRepository);
        SubscriptionBenefitService.BenefitScope scope = SubscriptionBenefitService.BenefitScope.CUSTOM;
        SubscriptionBenefitService.AppliedBenefitSnapshot snapshot;
        if (subscriptionBenefitService != null) {
            snapshot = subscriptionBenefitService.reserveAndApplyBenefits(order.getBuyer(),
                    SubscriptionBenefitService.UsageTargetType.CUSTOM_ORDER, order.getId(), scope, subtotal,
                    shippingFee, subtotal, BigDecimal.ZERO, preview -> validateCustomPrice(order, request,
                            OrderPricing.fromPreview(order.getQuoteVersion(), subtotal, BigDecimal.ZERO, preview)));
        } else {
            validateCustomPrice(order, request, OrderPricing.estimate(null, order.getBuyer(), scope,
                    order.getQuoteVersion(), subtotal, BigDecimal.ZERO, shippingFee));
            snapshot = SubscriptionBenefitService.AppliedBenefitSnapshot.empty(subtotal, shippingFee);
        }
        order.setPaymentMethod("PAYOS");
        order.setSubtotalAmount(snapshot.itemsSubtotal());
        order.setShippingFee(snapshot.baseShippingFee());
        order.setDiscountAmount(snapshot.discountAmount());
        order.setShippingDiscountAmount(snapshot.shippingDiscountAmount());
        order.setAppliedSubscriptionId(snapshot.appliedSubscriptionId());
        order.setAppliedSubscriptionName(snapshot.appliedSubscriptionName());
        order.setPriorityProduction(snapshot.priorityProduction());

        if (snapshot.fullyDiscounted()) {
            order.setZeroAmountSettledAt(Instant.now());
            subscriptionBenefitService.commitAppliedBenefits(
                    SubscriptionBenefitService.UsageTargetType.CUSTOM_ORDER, order.getId());
            return;
        }
        Payment payment = new Payment();
        payment.setCustomOrder(order);
        QuotePayment.prepare(paymentRepository, payment, snapshot.finalTotal());
    }

    private void syncParentCheckoutAfterQuote(UUID quotedCustomId, CustomOrder order, CheckoutOrder parent, Instant now) {
        List<CustomOrder> siblings = new ArrayList<>(customOrderRepository.findByCheckoutOrderId(parent.getId()));
        if (siblings.stream().noneMatch(c -> quotedCustomId.equals(c.getId()))) {
            siblings.add(order);
        }
        List<CustomOrder> activeSiblings = siblings.stream()
                .map(c -> quotedCustomId.equals(c.getId()) ? order : c)
                .filter(c -> !"CANCELLED".equalsIgnoreCase(c.getStatus()))
                .toList();

        BigDecimal customSubtotal = activeSiblings.stream()
                .map(CustomOrderMapper::resolveLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Order> marketOrders = orderRepository != null
                ? orderRepository.findByCheckoutOrderId(parent.getId()) : List.of();
        BigDecimal marketSubtotal = OrderPricing.marketplaceSubtotal(marketOrders);

        BigDecimal itemsSubtotal = customSubtotal.add(marketSubtotal);
        boolean allQuoted = !activeSiblings.isEmpty() && activeSiblings.stream()
                .allMatch(c -> CustomOrderMapper.resolveLineTotal(c).signum() > 0
                        && !"REQUESTED".equalsIgnoreCase(c.getStatus()));

        parent.setItemsSubtotal(itemsSubtotal);
        if (allQuoted) {
            BigDecimal shippingFee = OrderPricing.checkoutShippingFee(
                    parent, () -> CustomOrderMapper.resolveDefaultShippingFee(storeSettingRepository));
            parent.setShippingFee(shippingFee);
            parent.setTotalAmount(itemsSubtotal.add(shippingFee));
            if ("PENDING_QUOTE".equalsIgnoreCase(parent.getStatus())) {
                parent.setStatus("QUOTED");
            }
        } else {
            BigDecimal currentShip = parent.getShippingFee() != null ? parent.getShippingFee() : BigDecimal.ZERO;
            parent.setTotalAmount(itemsSubtotal.add(currentShip));
        }
        parent.setSummaryVersion((parent.getSummaryVersion() != null ? parent.getSummaryVersion() : 1L) + 1L);
        parent.setUpdatedAt(now);
        checkoutOrderRepository.save(parent);
        if (allQuoted && emailEvents != null) {
            String orderType = OrderPricing.checkoutOrderType(parent, true, !marketOrders.isEmpty());
            var pricing = OrderPricing.estimateCheckout(subscriptionBenefitService, parent, orderType,
                    parent.getSummaryVersion(), customSubtotal, marketSubtotal, parent.getShippingFee());
            emailEvents.enqueueQuotation("CHECKOUT_ORDER", parent.getId(), parent.getUser(), pricing, activeSiblings);
        }
    }

    private void validateCustomPrice(CustomOrder order, CustomOrderStatusRequestDTO request, PricingBreakdownDTO estimate) {
        if (request.expectedTotalPayable() == null
                || request.expectedTotalPayable().compareTo(estimate.totalPayable()) != 0) {
            throw customPricingConflict(order, CustomPrintErrorCode.CONFIRMATION_PRICE_CHANGED, estimate);
        }
        if (estimate.totalPayable().signum() != 0) {
            QuotePayment.requirePayable(estimate.totalPayable());
        }
    }

    private ApiException customPricingConflict(CustomOrder order, CustomPrintErrorCode error, PricingBreakdownDTO pricing) {
        return new ApiException(error, Map.of(
                "id", order.getId(), "quoteVersion", order.getQuoteVersion(), "pricingBreakdown", pricing));
    }
}
