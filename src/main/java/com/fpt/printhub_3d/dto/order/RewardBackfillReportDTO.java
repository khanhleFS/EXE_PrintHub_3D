package com.fpt.printhub_3d.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Báo cáo đối soát và cộng bù xu cho các đơn hàng đã hoàn tất (XU-09, E2E-04)")
public record RewardBackfillReportDTO(
        boolean dryRun,
        Instant executedAt,
        int scannedCount,
        int adjustedCount,
        int alreadyMatchedCount,
        int reconciliationRequiredCount,
        int totalPointsAdded,
        List<BackfillItemResult> items
) {
    public enum Action {
        WOULD_GRANT,
        GRANTED,
        NO_CHANGE,
        NEEDS_RECONCILIATION
    }

    @Builder
    public record BackfillItemResult(
            String sourceType,
            UUID sourceId,
            UUID userId,
            BigDecimal eligibleNetAmount,
            int expectedPoints,
            int alreadyGrantedPoints,
            int deltaPoints,
            Action action,
            String reason,
            List<UUID> inspectedChildIds
    ) {}
}
