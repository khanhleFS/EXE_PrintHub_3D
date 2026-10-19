package com.fpt.printhub_3d.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
@Schema(description = "Kết quả tạo link thanh toán PayOS")
public record CreatePaymentLinkResponseDTO(
        @Schema(description = "URL link thanh toán PayOS", example = "https://pay.payos.vn/web/abc123")
        String paymentLinkUrl,

        @Schema(description = "URL mã QR động để quét thanh toán", example = "https://pay.payos.vn/qr/abc123")
        String qrCodeUrl,

        @Schema(description = "Số tiền cần thanh toán (VND)", example = "200000")
        BigDecimal amount,

        @Schema(description = "Mã đơn hàng trên PayOS", example = "PH3D-1719590400123")
        String orderCode,

        @Schema(description = "Thời gian hết hạn của link thanh toán")
        Instant expiredAt,

        @Schema(description = "ID của Payment Attempt")
        java.util.UUID attemptId,

        @Schema(description = "Trạng thái tạo link: READY, INITIALIZING")
        String status,

        @Schema(description = "Số giây khuyến nghị thử lại (nếu status = INITIALIZING)")
        Integer retryAfterSeconds
) {
}
