package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.controller.api.FinanceAPI;
import com.fpt.printhub_3d.dto.finance.CommissionFundResponseDTO;
import com.fpt.printhub_3d.dto.finance.RevenueAnalyticsResponseDTO;
import com.fpt.printhub_3d.service.FinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Slf4j
@RestController
@RequiredArgsConstructor
public class FinanceController implements FinanceAPI {

    private final FinanceService financeService;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionFundResponseDTO>> getCommissionFund() {
        CommissionFundResponseDTO response = financeService.getCommissionFund();
        return ResponseEntity.ok(ApiResponse.<CommissionFundResponseDTO>builder()
                .code(200)
                .message("Lấy báo cáo quỹ hoa hồng thành công")
                .result(response)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RevenueAnalyticsResponseDTO>> getRevenueAnalytics(Instant from, Instant to) {
        RevenueAnalyticsResponseDTO response = financeService.getRevenueAnalytics(from, to);
        return ResponseEntity.ok(ApiResponse.<RevenueAnalyticsResponseDTO>builder()
                .code(200)
                .message("Lấy báo cáo doanh thu thành công")
                .result(response)
                .build());
    }
}
