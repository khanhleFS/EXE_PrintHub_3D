package com.fpt.printhub_3d.dto.authen;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin tài khoản hiển thị trong trang quản trị Admin")
public record AdminUserResponseDTO(
        UUID id,
        String name,
        String email,
        String role,
        boolean isLocked,
        LocalDateTime createdAt
) {}
