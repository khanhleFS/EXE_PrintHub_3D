package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.finance.FinancialReportDTO;
import com.fpt.printhub_3d.dto.finance.FinancialReportDTO.CompletionIssue;
import com.fpt.printhub_3d.dto.finance.FinancialReportDTO.ReconciliationItem;
import com.fpt.printhub_3d.dto.finance.FinancialReportPeriod;
import com.fpt.printhub_3d.entity.CheckoutOrder;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.SubscriptionPurchase;
import com.fpt.printhub_3d.repository.CheckoutOrderRepository;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialReportingService {
    private final PaymentRepository payments;
    private final CheckoutOrderRepository checkouts;
    private final OrderRepository orders;
    private final CustomOrderRepository customOrders;
    private final Clock clock;

    public FinancialReportDTO summarize(FinancialReportPeriod period) {
        CashTotals cash = summarizeCash(period);
        BigDecimal completedGoods = BigDecimal.ZERO;
        List<CompletionIssue> completionIssues = new ArrayList<>();
        for (CompletedGoods completed : completedOrders().toList()) {
            if (completed.completedAt() == null) {
                completionIssues.add(completionIssue(completed, "MISSING_COMPLETED_AT"));
            } else if (period.includes(completed.completedAt())) {
                if (completed.amount() == null) {
                    completionIssues.add(completionIssue(completed, "MISSING_GOODS_BREAKDOWN"));
                } else {
                    completedGoods = completedGoods.add(completed.amount());
                }
                if (!completed.zeroAmountPromo() && !cash.settledSources().contains(completed.source())) {
                    completionIssues.add(completionIssue(completed, "MISSING_SETTLED_RECEIPT"));
                }
            }
        }
        completionIssues.sort(Comparator.comparing(CompletionIssue::sourceType).thenComparing(CompletionIssue::sourceId));

        return new FinancialReportDTO(period, FinancialReportPeriod.ZONE.getId(), clock.instant(), cash.orderCash(),
                cash.subscriptionCash(), cash.orderCash().add(cash.subscriptionCash()), cash.shippingCash(),
                completedGoods, cash.pendingReconciliation(), cash.reconciliationItems(), List.copyOf(completionIssues));
    }

    public static boolean isSettledSubscriptionReceipt(Payment payment) {
        SubscriptionPurchase purchase = payment.getSubscriptionPurchase();
        return purchase != null && "PAID".equalsIgnoreCase(purchase.getStatus())
                && payment.getCheckoutOrder() == null && payment.getOrder() == null && payment.getCustomOrder() == null
                && payment.isSettledReceiptFor(purchase.getAmount(), "PAYOS");
    }

    public Map<UUID, Payment> settledSubscriptionReceipts() {
        return allocateReceipts().stream()
                .filter(receipt -> receipt.issue() == null && "SUBSCRIPTION_PURCHASE".equals(receipt.source().type()))
                .collect(Collectors.toMap(receipt -> receipt.source().id(), Receipt::payment));
    }

    private CashTotals summarizeCash(FinancialReportPeriod period) {
        BigDecimal orderCash = BigDecimal.ZERO;
        BigDecimal subscriptionCash = BigDecimal.ZERO;
        BigDecimal shippingCash = BigDecimal.ZERO;
        List<ReconciliationItem> issues = new ArrayList<>();
        Set<Source> settledSources = new HashSet<>();

        for (Receipt receipt : allocateReceipts()) {
            Payment payment = receipt.payment();
            if ("DUPLICATE_PAYOS_CODE".equals(receipt.issue())) {
                addIssue(issues, receipt, period, "DUPLICATE_PAYOS_CODE", null,
                        payment.getPaidAt() != null ? payment.getPaidAt() : payment.getReconciliationReceivedAt());
                continue;
            }
            if ("SUCCESS".equalsIgnoreCase(payment.getStatus())) {
                String reason = receipt.issue();
                if (reason != null) {
                    addIssue(issues, receipt, period, reason, positiveOrNull(payment.getAmount()), payment.getPaidAt());
                } else {
                    settledSources.add(receipt.source());
                    if (period.includes(payment.getPaidAt())) {
                        if ("SUBSCRIPTION_PURCHASE".equals(receipt.source().type())) {
                            subscriptionCash = subscriptionCash.add(payment.getAmount());
                        } else {
                            orderCash = orderCash.add(payment.getAmount());
                            if (receipt.shippingCash() != null && receipt.shippingCash().compareTo(payment.getAmount()) <= 0) {
                                shippingCash = shippingCash.add(receipt.shippingCash());
                            }
                        }
                    }
                }
            }
            if (Payment.PENDING_RECONCILIATION.equals(payment.getStatus()) || payment.getReconciliationAmount() != null) {
                addIssue(issues, receipt, period, payment.getReconciliationReason() != null
                                ? payment.getReconciliationReason() : "PENDING_RECONCILIATION",
                        positiveOrNull(payment.getReconciliationAmount()), payment.getReconciliationReceivedAt());
            }
        }

        BigDecimal reconciliation = issues.stream()
                .filter(item -> period.includes(item.receivedAt()))
                .map(ReconciliationItem::amountVnd).filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        issues.sort(Comparator.comparing(ReconciliationItem::receivedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ReconciliationItem::paymentId, Comparator.nullsLast(Comparator.naturalOrder())));
        return new CashTotals(orderCash, subscriptionCash, shippingCash, reconciliation, List.copyOf(issues), settledSources);
    }

    private List<Receipt> allocateReceipts() {
        // Select the canonical receipt before applying date, status, or buyer filters.
        List<Receipt> candidates = payments.findReportablePayments().stream()
                .map(this::classify)
                .sorted(Comparator.comparing((Receipt receipt) -> receipt.issue() != null)
                        .thenComparing(receipt -> receipt.payment().getPaidAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(receipt -> receipt.payment().getId(), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Set<UUID> paymentIds = new HashSet<>();
        Set<String> payosCodes = new HashSet<>();
        Set<Source> settledSources = new HashSet<>();
        List<Receipt> allocated = new ArrayList<>(candidates.size());
        for (Receipt candidate : candidates) {
            Payment payment = candidate.payment();
            if (payment.getId() != null && !paymentIds.add(payment.getId())) {
                continue;
            }
            String code = payment.getTransactionId();
            String issue = candidate.issue();
            if ("PAYOS".equalsIgnoreCase(payment.getGateway()) && code != null
                    && !code.isBlank() && !payosCodes.add(code.strip())) {
                issue = "DUPLICATE_PAYOS_CODE";
            } else if (issue == null && !settledSources.add(candidate.source())) {
                issue = "DUPLICATE_SETTLED_SOURCE";
            }
            allocated.add(new Receipt(payment, candidate.source(), candidate.shippingCash(), issue));
        }
        return allocated;
    }

    private Receipt classify(Payment payment) {
        CheckoutOrder checkout = payment.getCheckoutOrder();
        Order order = payment.getOrder();
        CustomOrder custom = payment.getCustomOrder();
        SubscriptionPurchase purchase = payment.getSubscriptionPurchase();
        if (checkout != null) {
            return orderReceipt(payment, new Source("CHECKOUT_ORDER", checkout.getId()), checkout.getStatus(),
                    checkout.getTotalAmount(), checkout.getPaymentMethod(),
                    netShipping(checkout.getShippingFee(), checkout.getShippingDiscountAmount()));
        }
        CheckoutOrder parent = order != null ? order.getCheckoutOrder() : custom != null ? custom.getCheckoutOrder() : null;
        if (parent != null) {
            return new Receipt(payment, new Source("CHECKOUT_ORDER", parent.getId()), null, "DUPLICATE_CHECKOUT_SOURCE");
        }
        if (order != null) {
            return orderReceipt(payment, new Source("ORDER", order.getId()), order.getStatus(),
                    order.getTotalAmount(), order.getPaymentMethod(),
                    netShipping(order.getShippingFee(), order.getShippingDiscountAmount()));
        }
        if (custom != null) {
            return orderReceipt(payment, new Source("CUSTOM_ORDER", custom.getId()), custom.getStatus(),
                    OrderPricing.customPayable(custom), custom.getPaymentMethod(),
                    netShipping(custom.getShippingFee(), custom.getShippingDiscountAmount()));
        }
        if (purchase != null) {
            return new Receipt(payment, new Source("SUBSCRIPTION_PURCHASE", purchase.getId()), null,
                    isSettledSubscriptionReceipt(payment) ? null : "INVALID_SUBSCRIPTION_RECEIPT");
        }
        return new Receipt(payment, new Source("UNKNOWN", null), null, "MISSING_PAYMENT_SOURCE");
    }

    private Receipt orderReceipt(Payment payment, Source source, String status, BigDecimal expected,
                                 String method, BigDecimal shippingCash) {
        if ("CANCELLED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status)) {
            return new Receipt(payment, source, null, "LATE_PAYMENT");
        }
        String issue = payment.isSettledReceiptFor(expected, method) ? null
                : payment.getPaidAt() == null ? "MISSING_PAID_AT" : "INVALID_ORDER_RECEIPT";
        return new Receipt(payment, source, shippingCash, issue);
    }

    private void addIssue(List<ReconciliationItem> issues, Receipt receipt, FinancialReportPeriod period,
                          String reason, BigDecimal amount, Instant receivedAt) {
        if (receivedAt == null || period.includes(receivedAt)) {
            issues.add(new ReconciliationItem(receipt.payment().getId(), receipt.payment().getTransactionId(),
                    receipt.source().type(), receipt.source().id(), reason, amount, receivedAt));
        }
    }

    private Stream<CompletedGoods> completedOrders() {
        var parent = checkouts.findByStatus("COMPLETED").stream().map(checkout -> new CompletedGoods(
                new Source("CHECKOUT_ORDER", checkout.getId()), checkout.getCompletedAt(),
                netGoods(checkout.getItemsSubtotal(), checkout.getDiscountAmount(), checkout.getTotalAmount(),
                        netShipping(checkout.getShippingFee(), checkout.getShippingDiscountAmount())),
                checkout.getZeroAmountSettledAt() != null));
        var marketplace = orders.findByStatusAndCheckoutOrderIsNull("COMPLETED").stream().map(order -> new CompletedGoods(
                new Source("ORDER", order.getId()), order.getCompletedAt(),
                netGoods(order.getSubtotalAmount(), order.getDiscountAmount(), order.getTotalAmount(),
                        netShipping(order.getShippingFee(), order.getShippingDiscountAmount())), false));
        var custom = customOrders.findByStatusAndCheckoutOrderIsNull("COMPLETED").stream().map(order -> new CompletedGoods(
                new Source("CUSTOM_ORDER", order.getId()), order.getCompletedAt(),
                netGoods(order.getSubtotalAmount(), order.getDiscountAmount(), null, null),
                order.getZeroAmountSettledAt() != null));
        return Stream.concat(parent, Stream.concat(marketplace, custom));
    }

    private BigDecimal netGoods(BigDecimal subtotal, BigDecimal discount, BigDecimal savedTotal, BigDecimal shipping) {
        if (subtotal != null && discount != null) {
            return subtotal.subtract(discount).max(BigDecimal.ZERO);
        }
        return savedTotal != null && shipping != null && savedTotal.compareTo(shipping) >= 0
                ? savedTotal.subtract(shipping) : null;
    }

    private BigDecimal netShipping(BigDecimal baseFee, BigDecimal discount) {
        return baseFee != null && discount != null ? baseFee.subtract(discount).max(BigDecimal.ZERO) : null;
    }

    private BigDecimal positiveOrNull(BigDecimal amount) {
        return amount != null && amount.signum() > 0 ? amount : null;
    }

    private CompletionIssue completionIssue(CompletedGoods completed, String reason) {
        return new CompletionIssue(completed.source().type(), completed.source().id(), reason, completed.completedAt());
    }

    private record Source(String type, UUID id) {}
    private record Receipt(Payment payment, Source source, BigDecimal shippingCash, String issue) {}
    private record CompletedGoods(Source source, Instant completedAt, BigDecimal amount, boolean zeroAmountPromo) {}
    private record CashTotals(BigDecimal orderCash, BigDecimal subscriptionCash, BigDecimal shippingCash,
                              BigDecimal pendingReconciliation, List<ReconciliationItem> reconciliationItems,
                              Set<Source> settledSources) {}
}
