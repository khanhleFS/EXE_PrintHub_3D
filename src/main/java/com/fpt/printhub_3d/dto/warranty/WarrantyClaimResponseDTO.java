package com.fpt.printhub_3d.dto.warranty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin yêu cầu bảo hành")
public record WarrantyClaimResponseDTO(
        UUID id,
        UUID orderId,
        String buyerName,
        String description,
        String imageUrl,
        String status,
        Instant createdAt
) {}
