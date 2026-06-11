package com.fpt.printhub_3d.dto.authen;

import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record LoginResponseDTO(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIn0.signature")
        String token,
        @Schema(example = "Bearer")
        String tokenType, // "Bearer"
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
        UUID userId,
        @Schema(example = "Nguyen Van A")
        String fullName,
        @Schema(example = "nguyenvana")
        String username,
        @Schema(example = "user@example.com")
        String email,
        @Schema(example = "USER")
        UserRole role // USER, AUTHOR, ADMIN (giúp FE hiển thị menu tương ứng ngay lập tức)
) {}

