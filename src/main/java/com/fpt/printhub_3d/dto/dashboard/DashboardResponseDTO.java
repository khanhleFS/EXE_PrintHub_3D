package com.fpt.printhub_3d.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(description = "Số liệu tổng quan dashboard")
public record DashboardResponseDTO(
        int orderCount,
        long completedCount,
        BigDecimal paidAmount,
        int customCount
) {}
