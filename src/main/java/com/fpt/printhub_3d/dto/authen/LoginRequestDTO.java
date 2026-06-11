package com.fpt.printhub_3d.dto.authen;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO (@NotBlank(message = "Email không được để trống.")
                               @Pattern(
                                       regexp = "^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$"
                                               + "|"
                                               + "^[a-zA-Z][a-zA-Z0-9_]{2,49}$",
                                       message = "Tên đăng nhập hoặc email không hợp lệ.")
                               @Schema(example = "user@example.com")
                                String userNameOrEmail,

                               @NotBlank(message = "Mật khẩu không được để trống.")
                               @Schema(example = "P@ssw0rd123")
                               String password){}
