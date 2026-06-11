package com.fpt.printhub_3d.dto.authen;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequestDTO(
        @NotBlank(message = "Email không được để trống.")
        @Email(message = "Email không đúng định dạng.")
        @Size(message = "Email phải có độ dài từ 10 đến 50 ký tự.", min = 10, max = 50)
        @Schema(example = "user@example.com")
        String email,

        @NotBlank(message = "Mật khẩu cũ không được để trống.")
        @Size(message = "Mật khẩu phải có độ dài từ 10 đến 50 ký tự.", min = 10, max = 50)
        @Schema(example = "OldP@ssw0rd123")
        String oldPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống.")
        @Size(min = 8, max = 50, message = "Mật khẩu phải từ 8 đến 50 ký tự.")
        @Pattern(regexp = ".*[A-Z].*", message = "Mật khẩu phải có ít nhất 1 chữ hoa.")
        @Pattern(regexp = ".*[a-z].*", message = "Mật khẩu phải có ít nhất 1 chữ thường.")
        @Pattern(regexp = ".*[0-9].*", message = "Mật khẩu phải có ít nhất 1 chữ số.")
        @Pattern(regexp = ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*", message = "Mật khẩu phải có ít nhất 1 ký tự đặc biệt.")
        @Pattern(regexp = "^\\S+$", message = "Mật khẩu không được chứa khoảng trắng.")
        @Schema(example = "NewP@ssw0rd123")
        String newPassword,

        @NotBlank(message = "Xác nhận mật khẩu không được để trống.")
        @Schema(example = "NewP@ssw0rd123")
        String confirmPassword
) {}
