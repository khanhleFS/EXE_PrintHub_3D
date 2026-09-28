package com.fpt.printhub_3d.dto.authen;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
@Schema(description = "Yêu cầu khóa hoặc mở khóa tài khoản")
public record UpdateUserLockRequestDTO(
        @NotNull(message = "Trạng thái khóa không được null")
        @Schema(description = "True để khóa tài khoản, false để mở khóa", example = "true")
        Boolean isLocked
) {}
