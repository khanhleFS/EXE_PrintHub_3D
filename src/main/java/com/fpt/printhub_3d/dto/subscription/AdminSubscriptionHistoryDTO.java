package com.fpt.printhub_3d.dto.subscription;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminSubscriptionHistoryDTO(List<Item> items, long totalElements, int page, int size, Summary summary) {
    public enum Method {
        CASH, POINTS_REDEEM
    }

    public enum Status {
        PAID, PENDING, CANCELLED, EXPIRED, PENDING_RECONCILIATION, REDEEMED
    }

    public record Summary(BigDecimal subscriptionCashVnd, long cashCount, long pointsRedeemCount) {}

    public record Item(
            String id,
            UUID userId,
            String buyerName,
            String buyerEmail,
            UUID planId,
            String planName,
            boolean planVerified,
            Method method,
            Status status,
            String purchaseStatus,
            Instant transactionAt,
            boolean legacyTimestamp,
            BigDecimal amountVnd,
            BigDecimal purchaseAmountVnd,
            long pointsSpent,
            String referenceId,
            UUID subscriptionId,
            UUID paymentId,
            String payosOrderCode,
            String description,
            String reconciliationReason
    ) {}
}
