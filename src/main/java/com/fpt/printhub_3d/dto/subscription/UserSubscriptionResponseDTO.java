package com.fpt.printhub_3d.dto.subscription;

import com.fpt.printhub_3d.entity.Enumeration.SubscriptionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin đăng ký gói của người dùng")
public record UserSubscriptionResponseDTO(
        UUID subscriptionId,
        UUID userId,
        String username,
        UUID planId,
        String planName,
        SubscriptionType planType,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Boolean isActive
) {
}
