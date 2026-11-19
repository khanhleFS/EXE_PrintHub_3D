package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CheckoutOrderErrorCode;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderBatchQuoteRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderConfirmRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderResponseDTO;
import com.fpt.printhub_3d.dto.order.PricingBreakdownDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.InventoryService;
import com.fpt.printhub_3d.service.NotificationService;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import com.fpt.printhub_3d.service.SubscriptionBenefitService.BenefitScope;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CheckoutOrderQuotationService {

    private final CheckoutOrderRepository checkoutOrderRepository;
    private final CustomOrderRepository customOrderRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final CheckoutOrderMapper checkoutOrderMapper;

    @Nullable
    private final PaymentRepository paymentRepository;
    @Nullable
    private final NotificationService notificationService;
    @Nullable
    private final SubscriptionBenefitService subscriptionBenefitService;
    @Nullable
    private final OrderEmailEvents emailEvents;

    public CheckoutOrderResponseDTO batchQuoteCheckoutOrder(User admin, UUID id, CheckoutOrderBatchQuoteRequestDTO request) {
        CheckoutOrder co = checkoutOrderRepository.findLockedById(id)
                .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.CHECKOUT_ORDER_NOT_FOUND));

        String currentParentStatus = co.getStatus() != null ? co.getStatus().toUpperCase() : "";
        if (!Set.of("PENDING_QUOTE", "QUOTED").contains(currentParentStatus)) {
            throw new ApiException(
                    CheckoutOrderErrorCode.INVALID_ORDER_TRANSITION,
                    "Không thể báo giá hoặc chỉnh sửa báo giá khi đơn hàng đang ở trạng thái " + co.getStatus()
            );
        }

        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Danh sách báo giá từng sản phẩm không được để trống");
        }

        List<CustomOrder> allCustoms = customOrderRepository.findByCheckoutOrderId(id);
        List<CustomOrder> activeCustoms = allCustoms.stream()
                .filter(c -> !"CANCELLED".equalsIgnoreCase(c.getStatus()))
                .toList();
        if (activeCustoms.isEmpty()) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Đơn hàng không có sản phẩm in custom nào đang hoạt động để báo giá");
        }

        Map<UUID, CustomOrder> activeMap = activeCustoms.stream()
                .collect(Collectors.toMap(CustomOrder::getId, c -> c));

        // Kiểm tra toàn bộ payload trước khi thay đổi DB để đảm bảo tính nguyên tử (atomic)
        Set<UUID> seenIds = new HashSet<>();
        for (CheckoutOrderBatchQuoteRequestDTO.ItemQuote itemQuote : request.items()) {
            if (itemQuote == null || itemQuote.customOrderId() == null) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "ID đơn in custom không hợp lệ");
            }
            if (!seenIds.add(itemQuote.customOrderId())) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Bị trùng lặp sản phẩm trong yêu cầu báo giá: " + itemQuote.customOrderId());
            }
            CustomOrder target = activeMap.get(itemQuote.customOrderId());
            if (target == null) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Sản phẩm " + itemQuote.customOrderId() + " không thuộc đơn cha hoặc đã bị hủy");
            }
            if (itemQuote.unitPrice() == null || itemQuote.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Đơn giá sản phẩm phải lớn hơn 0 VND");
            }
            String childStatus = target.getStatus() != null ? target.getStatus().toUpperCase() : "";
            if (!Set.of("REQUESTED", "QUOTED").contains(childStatus)) {
                throw new ApiException(
                        CheckoutOrderErrorCode.INVALID_ORDER_TRANSITION,
                        "Sản phẩm " + itemQuote.customOrderId() + " đang ở trạng thái " + target.getStatus() + ", không thể báo giá lại"
                );
            }
        }

        if (seenIds.size() != activeMap.size() || !seenIds.containsAll(activeMap.keySet())) {
            throw new ApiException(
                    CommonErrorCode.INVALID_INPUT,
                    "Vui lòng nhập đầy đủ đơn giá cho toàn bộ " + activeMap.size() + " sản phẩm in custom đang hoạt động trong đơn"
            );
        }

        Instant now = Instant.now();
        BigDecimal customSubtotal = BigDecimal.ZERO;
        boolean quoteChanged = !"QUOTED".equals(currentParentStatus);
        for (CheckoutOrderBatchQuoteRequestDTO.ItemQuote itemQuote : request.items()) {
            CustomOrder target = activeMap.get(itemQuote.customOrderId());
            int qty = OrderPricing.quoteQuantity(target);
            BigDecimal unitPrice = itemQuote.unitPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
            String normalizedNote = itemQuote.note() != null && !itemQuote.note().isBlank() ? itemQuote.note().trim() : null;
            customSubtotal = customSubtotal.add(lineTotal);
            if (!OrderPricing.quoteChanged(target, unitPrice, lineTotal, normalizedNote)) {
                continue;
            }
            quoteChanged = true;

            target.setUnitQuotedPrice(unitPrice);
            target.setQuotedPrice(lineTotal);
            target.setQuoteNote(normalizedNote);
            target.setQuotedAt(now);
            target.setProcessedBy(admin);
            target.setStatus("QUOTED");
            target.setUpdatedAt(now);
            customOrderRepository.save(target);
        }

        if (!quoteChanged) {
            return checkoutOrderMapper.toDTO(co, null);
        }
        List<Order> marketOrders = orderRepository.findByCheckoutOrderId(id);
        BigDecimal marketSubtotal = OrderPricing.marketplaceSubtotal(marketOrders);

        BigDecimal itemsSubtotal = customSubtotal.add(marketSubtotal);
        BigDecimal shippingFee = OrderPricing.checkoutShippingFee(co, checkoutOrderMapper::resolveDefaultShippingFee);
        BigDecimal totalAmount = itemsSubtotal.add(shippingFee);

        co.setItemsSubtotal(itemsSubtotal);
        co.setShippingFee(shippingFee);
        co.setTotalAmount(totalAmount);
        co.setStatus("QUOTED");
        co.setSummaryVersion((co.getSummaryVersion() != null ? co.getSummaryVersion() : 1L) + 1L);
        co.setUpdatedAt(now);
        checkoutOrderRepository.save(co);

        CheckoutOrderResponseDTO response = checkoutOrderMapper.toDTO(co, null);
        if (emailEvents != null) {
            emailEvents.enqueueQuotation("CHECKOUT_ORDER", id, co.getUser(), response.pricingBreakdown(), activeCustoms);
        }
        if (notificationService != null && co.getUser() != null) {
            notificationService.sendNotification(
                    co.getUser(),
                    "Đơn in 3D #" + id.toString().substring(0, 8) + " đã có báo giá",
                    "Xưởng đã báo giá chi tiết cho " + activeCustoms.size() + " sản phẩm in 3D. Tổng tạm tính: " + totalAmount + " VND (đã gồm phí ship).",
                    "CUSTOM_ORDER",
                    "/orders/" + id
            );
        }

        return response;
    }

    public CheckoutOrderResponseDTO confirmCheckoutOrder(User user, UUID id, CheckoutOrderConfirmRequestDTO request) {
        CheckoutOrder checkoutOrder = checkoutOrderRepository.findLockedById(id)
                .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.CHECKOUT_ORDER_NOT_FOUND));

        if (!checkoutOrder.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        String status = checkoutOrder.getStatus();
        if ("CANCELLED".equals(status) || "EXPIRED".equals(status)) {
            throw new ApiException(CheckoutOrderErrorCode.CHECKOUT_ORDER_NOT_FOUND);
        }

        Long version = checkoutOrder.getSummaryVersion();
        if (!Objects.equals(version, request.summaryVersion())) {
            throw checkoutConflict(checkoutOrder, CheckoutOrderErrorCode.CONFIRMATION_VERSION_CONFLICT,
                    checkoutOrderMapper.toDTO(checkoutOrder, null).pricingBreakdown());
        }

        String storedMethod = checkoutOrder.getPaymentMethod() != null ? checkoutOrder.getPaymentMethod() : "PAYOS";
        String paymentMethod = request.paymentMethod() != null && !request.paymentMethod().isBlank()
                ? request.paymentMethod().trim().toUpperCase(Locale.ROOT) : storedMethod.toUpperCase(Locale.ROOT);
        if (!"PAYOS".equals(paymentMethod) && !"COD".equals(paymentMethod)) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Phương thức thanh toán không hợp lệ");
        }

        if ("CONFIRMED".equals(status) || "PROCESSING".equals(status) || "PAID".equals(status)) {
            if (!paymentMethod.equalsIgnoreCase(storedMethod)) {
                throw new ApiException(CheckoutOrderErrorCode.INVALID_PAYMENT_STATE);
            }
            BigDecimal expected = request.expectedTotalPayable();
            if (expected != null && (checkoutOrder.getTotalAmount() == null
                    || expected.compareTo(checkoutOrder.getTotalAmount()) != 0)) {
                throw checkoutConflict(checkoutOrder, CheckoutOrderErrorCode.CONFIRMATION_PRICE_CHANGED,
                        checkoutOrderMapper.toDTO(checkoutOrder, null).pricingBreakdown());
            }
            if ("CONFIRMED".equals(status) && "COD".equals(paymentMethod)) {
                checkoutOrder.setStatus("PROCESSING");
                checkoutOrder.setUpdatedAt(Instant.now());
                checkoutOrderRepository.save(checkoutOrder);
            }
            return checkoutOrderMapper.toDTO(checkoutOrder, null);
        }
        if (!"PENDING_QUOTE".equals(status) && !"QUOTED".equals(status)) {
            throw new ApiException(CheckoutOrderErrorCode.INVALID_PAYMENT_STATE);
        }

        List<CustomOrder> customList = customOrderRepository.findByCheckoutOrderId(id);
        List<CustomOrder> activeCustoms = customList.stream()
                .filter(c -> !"CANCELLED".equalsIgnoreCase(c.getStatus()))
                .toList();
        if ((!customList.isEmpty() && activeCustoms.isEmpty())
                || activeCustoms.stream().anyMatch(c -> !"QUOTED".equals(c.getStatus())
                        || checkoutOrderMapper.resolveCustomLineTotal(c).signum() <= 0)) {
            throw new ApiException(CheckoutOrderErrorCode.CUSTOM_NOT_ALL_QUOTED);
        }
        if (!activeCustoms.isEmpty() && !"PAYOS".equals(paymentMethod)) {
            throw new ApiException(CheckoutOrderErrorCode.COD_NOT_ALLOWED_FOR_CUSTOM);
        }

        List<Order> marketOrders = orderRepository.findByCheckoutOrderId(id).stream()
                .filter(order -> !"CANCELLED".equalsIgnoreCase(order.getStatus()))
                .toList();
        for (Order order : marketOrders) {
            if (!"PENDING".equals(order.getStatus())) {
                throw new ApiException(CheckoutOrderErrorCode.MARKETPLACE_OUT_OF_STOCK);
            }
            for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
                Product product = productRepository.findById(item.getProduct().getId())
                        .orElseThrow(() -> new ApiException(CheckoutOrderErrorCode.MARKETPLACE_OUT_OF_STOCK));
                if (product.getPrice().compareTo(item.getUnitPrice()) != 0) {
                    throw new ApiException(CheckoutOrderErrorCode.MARKETPLACE_PRICE_CHANGED);
                }
            }
        }

        BigDecimal customSubtotal = activeCustoms.stream().map(checkoutOrderMapper::resolveCustomLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal marketSubtotal = OrderPricing.marketplaceSubtotal(marketOrders);
        BigDecimal subtotal = customSubtotal.add(marketSubtotal);
        BigDecimal shippingFee = OrderPricing.checkoutShippingFee(checkoutOrder, checkoutOrderMapper::resolveDefaultShippingFee);
        BenefitScope scope = activeCustoms.isEmpty() ? BenefitScope.MARKETPLACE
                : (marketOrders.isEmpty() ? BenefitScope.CUSTOM : BenefitScope.MIXED);

        SubscriptionBenefitService.AppliedBenefitSnapshot snapshot;
        if (subscriptionBenefitService != null) {
            snapshot = subscriptionBenefitService.reserveAndApplyBenefits(user,
                    SubscriptionBenefitService.UsageTargetType.CHECKOUT_ORDER, id, scope, subtotal,
                    shippingFee, customSubtotal, marketSubtotal, preview ->
                            validateCheckoutPrice(checkoutOrder, request, paymentMethod,
                                    OrderPricing.fromPreview(version, customSubtotal, marketSubtotal, preview)));
        } else {
            PricingBreakdownDTO estimate = OrderPricing.estimate(null, user, scope, version, customSubtotal,
                    marketSubtotal, shippingFee);
            validateCheckoutPrice(checkoutOrder, request, paymentMethod, estimate);
            snapshot = SubscriptionBenefitService.AppliedBenefitSnapshot.empty(subtotal, shippingFee);
        }

        checkoutOrder.setItemsSubtotal(snapshot.itemsSubtotal());
        checkoutOrder.setShippingFee(snapshot.baseShippingFee());
        checkoutOrder.setDiscountAmount(snapshot.discountAmount());
        checkoutOrder.setShippingDiscountAmount(snapshot.shippingDiscountAmount());
        checkoutOrder.setAppliedSubscriptionId(snapshot.appliedSubscriptionId());
        checkoutOrder.setAppliedSubscriptionName(snapshot.appliedSubscriptionName());
        checkoutOrder.setPriorityProduction(snapshot.priorityProduction());
        checkoutOrder.setTotalAmount(snapshot.finalTotal());
        checkoutOrder.setPaymentMethod(paymentMethod);
        Instant now = Instant.now();
        checkoutOrder.setUpdatedAt(now);

        boolean zeroAmountSettlement = snapshot.fullyDiscounted();
        if (zeroAmountSettlement) {
            settleZeroAmountCheckout(checkoutOrder, now);
        } else if ("PAYOS".equals(paymentMethod)) {
            checkoutOrder.setStatus("CONFIRMED");
            prepareCheckoutPayment(checkoutOrder);
        } else {
            checkoutOrder.setStatus("PROCESSING");
        }
        checkoutOrderRepository.save(checkoutOrder);
        distributeSnapshotToChildren(checkoutOrder, activeCustoms, marketOrders, snapshot);

        for (CustomOrder custom : activeCustoms) {
            custom.setStatus(zeroAmountSettlement ? "PAID" : "ACCEPTED");
            custom.setUpdatedAt(now);
            customOrderRepository.save(custom);
        }
        if (zeroAmountSettlement || "COD".equals(paymentMethod)) {
            for (Order order : marketOrders) {
                if (zeroAmountSettlement) {
                    order.setStatus("PAID");
                    if (order.getSubtotalAmount() == null) {
                        order.setSubtotalAmount(order.getTotalAmount());
                    }
                    order.setTotalAmount(BigDecimal.ZERO);
                    order.setPaymentDueAt(null);
                    order.setUpdatedAt(now);
                    orderRepository.save(order);
                }
                inventoryService.confirmReservation(order.getId());
            }
        }
        return checkoutOrderMapper.toDTO(checkoutOrder, null);
    }

    public void settleZeroAmountCheckout(CheckoutOrder checkout, Instant settledAt) {
        checkout.setZeroAmountSettledAt(settledAt);
        checkout.setStatus("PAID");
        checkout.setPaymentExpiresAt(null);
        checkout.setReservationExpiresAt(null);
        if (subscriptionBenefitService != null) {
            subscriptionBenefitService.commitAppliedBenefits(
                    SubscriptionBenefitService.UsageTargetType.CHECKOUT_ORDER, checkout.getId());
        }
    }

    public void prepareCheckoutPayment(CheckoutOrder checkout) {
        if (paymentRepository == null) {
            return;
        }
        Payment payment = new Payment();
        payment.setCheckoutOrder(checkout);
        QuotePayment.prepare(paymentRepository, payment, checkout.getTotalAmount());
        checkout.setPaymentExpiresAt(payment.getExpiresAt());
    }

    private void validateCheckoutPrice(CheckoutOrder checkout, CheckoutOrderConfirmRequestDTO request,
                                       String paymentMethod, PricingBreakdownDTO estimate) {
        if (request.expectedTotalPayable() == null
                || request.expectedTotalPayable().compareTo(estimate.totalPayable()) != 0) {
            throw checkoutConflict(checkout, CheckoutOrderErrorCode.CONFIRMATION_PRICE_CHANGED, estimate);
        }
        if ("PAYOS".equals(paymentMethod) && estimate.totalPayable().signum() != 0) {
            QuotePayment.requirePayable(estimate.totalPayable());
        }
    }

    private ApiException checkoutConflict(CheckoutOrder checkout, CheckoutOrderErrorCode error,
                                          PricingBreakdownDTO pricing) {
        return pricing == null ? new ApiException(error) : new ApiException(error, Map.of(
                "id", checkout.getId(), "summaryVersion", pricing.quoteVersion(), "pricingBreakdown", pricing));
    }

    private void distributeSnapshotToChildren(
            CheckoutOrder co,
            List<CustomOrder> activeCustoms,
            List<Order> marketOrders,
            SubscriptionBenefitService.AppliedBenefitSnapshot snap
    ) {
        if (snap == null) {
            return;
        }
        BigDecimal totalDiscount = snap.discountAmount() != null ? snap.discountAmount() : BigDecimal.ZERO;
        BigDecimal totalItemsSub = snap.itemsSubtotal() != null ? snap.itemsSubtotal() : BigDecimal.ZERO;
        BigDecimal remainingDisc = totalDiscount;

        int totalChildren = activeCustoms.size() + marketOrders.size();
        int idx = 0;

        for (CustomOrder c : activeCustoms) {
            idx++;
            BigDecimal cSub = checkoutOrderMapper.resolveCustomLineTotal(c);
            BigDecimal cDisc = allocateShare(cSub, totalItemsSub, totalDiscount, remainingDisc, idx == totalChildren);
            remainingDisc = remainingDisc.subtract(cDisc).max(BigDecimal.ZERO);

            c.setSubtotalAmount(cSub);
            c.setDiscountAmount(cDisc);
            c.setAppliedSubscriptionId(snap.appliedSubscriptionId());
            c.setAppliedSubscriptionName(snap.appliedSubscriptionName());
            c.setPriorityProduction(snap.priorityProduction());
        }

        for (Order o : marketOrders) {
            idx++;
            BigDecimal oSub = o.getSubtotalAmount() != null
                    ? o.getSubtotalAmount()
                    : (o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO);
            BigDecimal oDisc = allocateShare(oSub, totalItemsSub, totalDiscount, remainingDisc, idx == totalChildren);
            remainingDisc = remainingDisc.subtract(oDisc).max(BigDecimal.ZERO);

            o.setSubtotalAmount(oSub);
            o.setDiscountAmount(oDisc);
            o.setAppliedSubscriptionId(snap.appliedSubscriptionId());
            o.setAppliedSubscriptionName(snap.appliedSubscriptionName());
            o.setPriorityProduction(snap.priorityProduction());
            o.setUpdatedAt(Instant.now());
            orderRepository.save(o);
        }
    }

    private BigDecimal allocateShare(
            BigDecimal lineSub,
            BigDecimal totalSub,
            BigDecimal totalDiscount,
            BigDecimal remainingDisc,
            boolean isLast
    ) {
        if (totalDiscount.compareTo(BigDecimal.ZERO) <= 0 || totalSub.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (isLast) {
            return remainingDisc.min(lineSub).max(BigDecimal.ZERO);
        }
        BigDecimal ratioDisc = totalDiscount.multiply(lineSub)
                .divide(totalSub, 0, RoundingMode.HALF_UP);
        return ratioDisc.min(remainingDisc).min(lineSub).max(BigDecimal.ZERO);
    }
}
