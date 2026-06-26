package com.fpt.printhub_3d.dto.subscription;

import com.fpt.printhub_3d.entity.Enumeration.SubscriptionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin gói dịch vụ thành viên/quảng cáo")
public record SubscriptionPlanResponseDTO(
        UUID id,
        SubscriptionType type,
        String name,
        BigDecimal price,
        String benefits,
        Integer requiredPoints,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
