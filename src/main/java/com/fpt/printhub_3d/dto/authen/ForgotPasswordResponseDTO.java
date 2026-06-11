package com.fpt.printhub_3d.dto.authen;

import io.swagger.v3.oas.annotations.media.Schema;

public record ForgotPasswordResponseDTO(
        @Schema(example = "user@example.com")
        String email,
        @Schema(example = "Mật khẩu mới đã được gửi đến email của bạn thành công.")
        String message,
        @Schema(example = "true")
        boolean success,
        @Schema(example = "5")
        Integer minutesToExpire
) {
}
