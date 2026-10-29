package com.fpt.printhub_3d.dto.point;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin số dư ví điểm thưởng của người dùng")
public record PointWalletResponseDTO(
        UUID userId,
        Integer balance,
        Integer availableToSpend,
        LocalDateTime updatedAt
) {}

