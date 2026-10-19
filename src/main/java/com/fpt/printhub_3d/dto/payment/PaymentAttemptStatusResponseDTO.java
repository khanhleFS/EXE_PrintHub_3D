package com.fpt.printhub_3d.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin trạng thái của Payment Attempt phục vụ polling và điều phối thanh toán")
public record PaymentAttemptStatusResponseDTO(
        @Schema(description = "ID của lần thanh toán (Attempt ID)")
        UUID attemptId,

        @Schema(description = "Mã đơn hàng trên cổng thanh toán (Provider Code)")
        Long providerCode,

        @Schema(description = "Trạng thái kỹ thuật của attempt: INITIALIZING, READY, SUCCESS, FAILED, PENDING_VERIFY, RECONCILIATION_REQUIRED, CANCELLED, EXPIRED")
        String status,

        @Schema(description = "Phạm vi thanh toán: ALL, MARKETPLACE, CUSTOM")
        String scope,

        @Schema(description = "Số tiền phải thanh toán")
        BigDecimal amount,

        @Schema(description = "URL trang thanh toán PayOS (khi status = READY)")
        String checkoutUrl,

        @Schema(description = "Dữ liệu hoặc link mã QR (khi status = READY)")
        String qrCode,

        @Schema(description = "Thời gian hết hạn của attempt")
        Instant expiresAt,

        @Schema(description = "Thời gian khách nên kiểm tra lại nếu status = INITIALIZING (giây)")
        Integer retryAfterSeconds,

        @Schema(description = "Thông điệp hướng dẫn cho client")
        String message
) {}
