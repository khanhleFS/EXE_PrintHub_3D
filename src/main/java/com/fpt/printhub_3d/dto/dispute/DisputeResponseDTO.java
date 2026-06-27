package com.fpt.printhub_3d.dto.dispute;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record DisputeResponseDTO(
        UUID id,
        UUID orderId,
        UUID filedById,
        String filedByName,
        String description,
        String evidenceUrl,
        String status,
        String resolutionNote,
        BigDecimal refundAmount,
        String refundType,
        Instant createdAt,
        Instant updatedAt
) {
}
