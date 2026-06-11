package com.fpt.printhub_3d.dto.authen;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record RegisterResponseDTO(
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
        UUID userId,
        @Schema(example = "user@example.com")
        String email,
        @Schema(example = "Đăng ký thành công. Vui lòng kiểm tra OTP trong email.")
        String message, // "Đăng ký thành công. Vui lòng kiểm tra OTP trong email."
        @Schema(example = "true")
        boolean requiresOtpVerification
) {}
