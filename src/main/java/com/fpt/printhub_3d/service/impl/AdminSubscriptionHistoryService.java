package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.dto.finance.FinancialReportPeriod;
import com.fpt.printhub_3d.dto.subscription.AdminSubscriptionHistoryDTO;
import com.fpt.printhub_3d.dto.subscription.AdminSubscriptionHistoryDTO.Item;
import com.fpt.printhub_3d.dto.subscription.AdminSubscriptionHistoryDTO.Method;
import com.fpt.printhub_3d.dto.subscription.AdminSubscriptionHistoryDTO.Status;
import com.fpt.printhub_3d.dto.subscription.AdminSubscriptionHistoryDTO.Summary;
import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.PointTransaction;
import com.fpt.printhub_3d.entity.PointWallet;
import com.fpt.printhub_3d.entity.SubscriptionPurchase;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.repository.PointTransactionRepository;
import com.fpt.printhub_3d.repository.SubscriptionPurchaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminSubscriptionHistoryService {
    private static final String LEGACY_REDEEM_DESCRIPTION = "Đổi điểm lấy gói hội viên:%";
    private static final Comparator<Item> HISTORY_ORDER = Comparator.comparing(Item::transactionAt,
            Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Item::id, Comparator.reverseOrder());

    private final SubscriptionPurchaseRepository purchases;
    private final PointTransactionRepository pointTransactions;
    private final PaymentRepository payments;
    private final FinancialReportingService financialReporting;
    private final ZoneId legacyPointsZone;

    public AdminSubscriptionHistoryService(SubscriptionPurchaseRepository purchases,
                                           PointTransactionRepository pointTransactions, PaymentRepository payments,
                                           FinancialReportingService financialReporting,
                                           @Value("${app.membership-history.legacy-points-zone:}") String legacyPointsZone) {
        this.purchases = purchases;
        this.pointTransactions = pointTransactions;
        this.payments = payments;
        this.financialReporting = financialReporting;
        this.legacyPointsZone = legacyPointsZone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(legacyPointsZone);
    }

    public AdminSubscriptionHistoryDTO getHistory(String q, UUID userId, UUID planId, Method method, Status status,
                                                  FinancialReportPeriod period, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Trang phải từ 0 và kích thước trang từ 1 đến 100.");
        }
        Map<String, Item> candidates = new LinkedHashMap<>();
        if (method != Method.POINTS_REDEEM) {
            addCashPurchases(candidates);
        }
        if (method != Method.CASH) {
            for (PointTransaction transaction : pointTransactions.findForSubscriptionHistory(
                    PointTransactionType.REDEEM, LEGACY_REDEEM_DESCRIPTION)) {
                Item row = redemptionRow(transaction);
                candidates.putIfAbsent(row.id(), row);
            }
        }
        String query = q != null ? q.strip().toLowerCase(Locale.ROOT) : "";
        List<Item> filtered = candidates.values().stream()
                .filter(row -> (userId == null || userId.equals(row.userId()))
                        && (planId == null || planId.equals(row.planId()))
                        && (status == null || status == row.status())
                        && period.includes(row.transactionAt())
                        && matchesQuery(row, query))
                .sorted(HISTORY_ORDER)
                .toList();

        long cashCount = filtered.stream().filter(row -> row.method() == Method.CASH).count();
        BigDecimal cash = filtered.stream().map(Item::amountVnd).reduce(BigDecimal.ZERO, BigDecimal::add);
        int start = (int) Math.min((long) page * size, filtered.size());
        int end = (int) Math.min((long) start + size, filtered.size());
        return new AdminSubscriptionHistoryDTO(List.copyOf(filtered.subList(start, end)), filtered.size(), page, size,
                new Summary(cash, cashCount, filtered.size() - cashCount));
    }

    private void addCashPurchases(Map<String, Item> candidates) {
        List<SubscriptionPurchase> purchaseRows = purchases.findForTransactionHistory();
        if (purchaseRows.isEmpty()) {
            return;
        }
        Map<UUID, Payment> settled = financialReporting.settledSubscriptionReceipts();
        Map<UUID, Payment> byPurchase = payments.findBySubscriptionPurchaseIsNotNull().stream()
                .collect(Collectors.toMap(payment -> payment.getSubscriptionPurchase().getId(), payment -> payment,
                        (first, duplicate) -> first));
        for (SubscriptionPurchase purchase : purchaseRows) {
            Payment receipt = settled.get(purchase.getId());
            Item row = cashRow(purchase, receipt, receipt != null ? receipt : byPurchase.get(purchase.getId()));
            candidates.putIfAbsent(row.id(), row);
        }
    }

    private Item cashRow(SubscriptionPurchase purchase, Payment receipt, Payment payment) {
        User buyer = purchase.getUser();
        String planName = purchase.getPlanNameSnapshot();
        UUID planId = purchase.getPlan().getId();
        Status status = cashStatus(purchase.getStatus(), receipt, payment);
        String reconciliation = payment != null ? payment.getReconciliationReason() : null;
        if (status == Status.PENDING_RECONCILIATION && reconciliation == null) {
            reconciliation = payment == null ? "MISSING_PAYMENT_EVIDENCE" : "UNVERIFIED_PAYMENT";
        }
        String id = purchase.getId().toString();
        return new Item(id, buyer.getId(), buyer.getFullName(), buyer.getEmail(), planId, planName,
                planId != null && hasText(planName), Method.CASH, status, purchase.getStatus(),
                receipt != null ? receipt.getPaidAt() : purchase.getCreatedAt(), false,
                receipt != null ? receipt.getAmount() : BigDecimal.ZERO, purchase.getAmount(), 0L, id, null,
                payment != null ? payment.getId() : null, payment != null ? payment.getTransactionId() : null,
                null, reconciliation);
    }

    private Status cashStatus(String purchaseStatus, Payment receipt, Payment payment) {
        if (receipt != null) {
            return Status.PAID;
        }
        if (payment != null && (Payment.PENDING_RECONCILIATION.equalsIgnoreCase(payment.getStatus())
                || "SUCCESS".equalsIgnoreCase(payment.getStatus()) || payment.getReconciliationAmount() != null)) {
            return Status.PENDING_RECONCILIATION;
        }
        return switch (purchaseStatus) {
            case "PENDING" -> Status.PENDING;
            case "CANCELLED" -> Status.CANCELLED;
            case "EXPIRED" -> Status.EXPIRED;
            default -> Status.PENDING_RECONCILIATION;
        };
    }

    private Item redemptionRow(PointTransaction transaction) {
        PointWallet wallet = transaction.getPointWallet();
        User buyer = wallet != null ? wallet.getUser() : null;
        UUID planId = transaction.getReferencePlanId();
        String planName = transaction.getReferencePlanName();
        boolean legacyTimestamp = transaction.getOccurredAt() == null;
        Instant transactionAt = legacyTimestamp
                ? transaction.getCreatedAt().atZone(legacyPointsZone).toInstant() : transaction.getOccurredAt();
        String id = transaction.getId().toString();
        return new Item(id, wallet != null ? wallet.getUserId() : null, buyer != null ? buyer.getFullName() : null,
                buyer != null ? buyer.getEmail() : null, planId, planName, planId != null && hasText(planName),
                Method.POINTS_REDEEM, Status.REDEEMED, null, transactionAt, legacyTimestamp,
                BigDecimal.ZERO, null, -(long) transaction.getAmount(), id,
                transaction.getReferenceSubscriptionId(), null, null, transaction.getDescription(), null);
    }

    private boolean matchesQuery(Item row, String query) {
        return query.isEmpty()
                || contains(row.buyerName(), query)
                || contains(row.buyerEmail(), query)
                || contains(row.userId() != null ? row.userId().toString() : null, query)
                || contains(row.referenceId(), query)
                || contains(row.payosOrderCode(), query)
                || contains(row.paymentId() != null ? row.paymentId().toString() : null, query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
