package com.fpt.printhub_3d.dto.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Yêu cầu tạo đơn mua gói hội viên bằng tiền mặt")
public record CreateSubscriptionPurchaseRequestDTO(
        @NotNull(message = "ID gói dịch vụ không được để trống")
        @Schema(description = "ID gói dịch vụ cần mua", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID planId,

        @Schema(description = "Mã chống gửi trùng lặp (Idempotency Key)", example = "660e8400-e29b-41d4-a716-446655440001")
        String idempotencyKey
) {
    public CreateSubscriptionPurchaseRequestDTO() {
        this(null, null);
    }
}

