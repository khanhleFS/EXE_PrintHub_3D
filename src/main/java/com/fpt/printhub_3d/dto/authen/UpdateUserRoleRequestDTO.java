package com.fpt.printhub_3d.dto.authen;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
@Schema(description = "Yêu cầu thay đổi vai trò tài khoản")
public record UpdateUserRoleRequestDTO(
        @NotBlank(message = "Vai trò không được để trống")
        @Schema(description = "Vai trò mới (USER, ADMIN)", example = "ADMIN")
        String role
) {}
