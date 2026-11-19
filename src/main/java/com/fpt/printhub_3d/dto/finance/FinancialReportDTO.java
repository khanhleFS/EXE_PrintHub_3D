package com.fpt.printhub_3d.dto.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FinancialReportDTO(
        FinancialReportPeriod period,
        String timeZone,
        Instant generatedAt,
        BigDecimal orderCashVnd,
        BigDecimal subscriptionCashVnd,
        BigDecimal totalCashVnd,
        BigDecimal shippingCashVnd,
        BigDecimal completedGoodsValueVnd,
        BigDecimal pendingReconciliationVnd,
        List<ReconciliationItem> reconciliationItems,
        List<CompletionIssue> completionIssues
) {
    public record ReconciliationItem(UUID paymentId, String orderCode, String sourceType, UUID sourceId,
                                     String reason, BigDecimal amountVnd, Instant receivedAt) {}

    public record CompletionIssue(String sourceType, UUID sourceId, String reason, Instant completedAt) {}
}
