package com.fpt.printhub_3d.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Payload IPN webhook từ cổng PayOS")
public record PayOSWebhookRequestDTO(
        @Schema(description = "Mã đơn hàng trên PayOS", example = "PH3D-1719590400123")
        String orderCode,

        @Schema(description = "Số tiền giao dịch (VND)", example = "200000")
        BigDecimal amount,

        @Schema(description = "Trạng thái giao dịch từ PayOS (SUCCESS, FAILED, CANCELLED)", example = "SUCCESS")
        String status,

        @Schema(description = "Mã giao dịch PayOS", example = "TXN-20260628-001")
        String transactionId,

        @Schema(description = "Chữ ký xác thực webhook từ PayOS", example = "a1b2c3d4e5f6...")
        String signature
) {
}
