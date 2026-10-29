package com.fpt.printhub_3d.dto.point;

import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@Schema(description = "Bản ghi biến động số dư điểm thưởng (Audit Ledger)")
public record PointTransactionResponseDTO(
        Long id,
        Integer amount,
        PointTransactionType type,
        String description,
        LocalDateTime createdAt
) {}

