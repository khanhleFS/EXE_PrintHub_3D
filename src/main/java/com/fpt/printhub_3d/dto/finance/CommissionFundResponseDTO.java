package com.fpt.printhub_3d.dto.finance;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CommissionFundResponseDTO(
        BigDecimal totalCommissionFee,
        BigDecimal fundAllocationRate,
        BigDecimal commissionFundBalance,
        Long totalOrders
) {
}
