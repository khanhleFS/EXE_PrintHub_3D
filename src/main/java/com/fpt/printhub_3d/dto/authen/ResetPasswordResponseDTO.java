package com.fpt.printhub_3d.dto.authen;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record ResetPasswordResponseDTO(
        @Schema(example = "user@example.com")
        String email,
        @Schema(example = "Mật khẩu đã được cập nhật thành công.")
        String message,
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
        UUID userId,
        @Schema(example = "/login")
        String redirectUrl
) {
}
