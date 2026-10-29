package com.fpt.printhub_3d.dto.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin giao dịch mua gói hội viên bằng tiền mặt")
public record SubscriptionPurchaseResponseDTO(
        @Schema(description = "ID giao dịch mua gói")
        UUID purchaseId,

        @Schema(description = "ID người dùng")
        UUID userId,

        @Schema(description = "ID gói dịch vụ")
        UUID planId,

        @Schema(description = "Tên gói dịch vụ")
        String planName,

        @Schema(description = "Giá gói tại thời điểm mua (VND)")
        BigDecimal amount,

        @Schema(description = "Thời hạn gói (ngày)")
        Integer durationDays,

        @Schema(description = "Trạng thái giao dịch: PENDING, PAID, CANCELLED, EXPIRED")
        String status,

        @Schema(description = "Mã idempotency")
        String idempotencyKey,

        @Schema(description = "Thời điểm tạo đơn mua")
        Instant createdAt,

        @Schema(description = "Thời điểm thanh toán thành công")
        Instant settledAt
) {
}

