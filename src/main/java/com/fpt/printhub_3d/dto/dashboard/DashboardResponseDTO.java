package com.fpt.printhub_3d.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
@Schema(description = "Số liệu tổng quan dashboard")
public record DashboardResponseDTO(
        int orderCount,
        long completedCount,
        BigDecimal paidAmount,
        int customCount,
        BigDecimal totalRevenue,
        int activePrintingOrders,
        int pendingDisputesCount,
        long totalUsersCount,
        List<RecentPrintJobDTO> recentJobs
) {
    @Builder
    public record RecentPrintJobDTO(
            String id,
            String item,
            String factory,
            String status,
            String type,
            BigDecimal price
    ) {}
}
