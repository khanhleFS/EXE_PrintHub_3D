package com.fpt.printhub_3d.dto.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Tổng quan gói hội viên và điểm thưởng của người dùng")
public record UserSubscriptionSummaryDTO(
        int points,
        List<UserSubscriptionResponseDTO> subscriptions
) {}
