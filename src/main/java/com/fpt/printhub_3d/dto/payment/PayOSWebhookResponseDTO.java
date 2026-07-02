package com.fpt.printhub_3d.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Phản hồi xác nhận đã nhận webhook từ PayOS")
public record PayOSWebhookResponseDTO(
        @Schema(description = "Kết quả xử lý webhook", example = "true")
        boolean success,

        @Schema(description = "Thông báo chi tiết", example = "Webhook xử lý thành công")
        String message
) {
}
