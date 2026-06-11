package com.fpt.printhub_3d.dto.authen;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerifyOTPRequestDTO(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        @Schema(example = "user@example.com")
        String email,

        @NotBlank(message = "OTP không được để trống")
        @Schema(example = "123456")
        String otpCode
) {}
