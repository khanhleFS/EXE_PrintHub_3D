package com.fpt.printhub_3d.dto.finance;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record RevenueAnalyticsResponseDTO(
        Instant from,
        Instant to,
        BigDecimal orderCommissionRevenue,
        BigDecimal makerSubscriptionRevenue,
        BigDecimal customerSubscriptionRevenue,
        BigDecimal totalRevenue
) {
}
