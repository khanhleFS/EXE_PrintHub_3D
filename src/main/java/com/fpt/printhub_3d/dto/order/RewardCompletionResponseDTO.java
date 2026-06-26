package com.fpt.printhub_3d.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Kết quả cộng điểm thưởng cho đơn hàng đã hoàn tất")
public record RewardCompletionResponseDTO(
        UUID orderId,
        UUID customerId,
        String customerName,
        Integer rewardPointsEarned,
        Integer totalRewardPoints,
        Boolean alreadyProcessed
) {
}
