package com.fpt.printhub_3d.dto.subscription;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Thông tin bổ sung khi đổi điểm lấy gói hội viên (hỗ trợ Idempotency Key)")
public record RedeemSubscriptionRequestDTO(
        @Schema(description = "Mã chống gửi trùng lặp (Idempotency Key)", example = "660e8400-e29b-41d4-a716-446655440001")
        String idempotencyKey
) {
    public RedeemSubscriptionRequestDTO() {
        this(null);
    }
}

