package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.finance.CommissionFundResponseDTO;
import com.fpt.printhub_3d.dto.finance.RevenueAnalyticsResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

@Tag(name = "Finance Admin APIs", description = "APIs for commission fund and revenue analytics")
public interface FinanceAPI {

    @Operation(
            summary = "Get commission fund report",
            description = "Admin kiểm tra số dư quỹ nội bộ trích lập 1% từ phí hoa hồng giao dịch sàn.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/api/admin/finance/commission-fund")
    ResponseEntity<ApiResponse<CommissionFundResponseDTO>> getCommissionFund();

    @Operation(
            summary = "Get revenue analytics",
            description = "Admin xem báo cáo doanh thu theo khoảng thời gian.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/api/admin/analytics/revenue")
    ResponseEntity<ApiResponse<RevenueAnalyticsResponseDTO>> getRevenueAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to);
}
