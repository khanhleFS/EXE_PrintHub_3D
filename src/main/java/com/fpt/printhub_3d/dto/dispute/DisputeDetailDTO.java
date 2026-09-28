package com.fpt.printhub_3d.dto.dispute;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin chi tiết khiếu nại")
public record DisputeDetailDTO(
        UUID id,
        UUID orderId,
        String buyerName,
        BigDecimal amount,
        String description,
        String evidenceUrl,
        String status,
        String resolutionNote,
        BigDecimal refundAmount,
        Instant createdAt
) {}
