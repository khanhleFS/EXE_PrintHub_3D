package com.fpt.printhub_3d.dto.finance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Builder
@Schema(description = "Báo cáo tổng quan tài chính và doanh thu sàn")
public record FinanceSummaryResponseDTO(
        BigDecimal totalGmv,
        BigDecimal platformCommission,
        BigDecimal factoryPayout,
        BigDecimal studentWalletBalance,
        long totalOrders,
        List<FinancialTransactionDTO> recentTransactions
) {
    @Builder
    public record FinancialTransactionDTO(
            String id,
            String user,
            String type,
            BigDecimal amount,
            String status,
            String gateway,
            Instant date
    ) {}
}
