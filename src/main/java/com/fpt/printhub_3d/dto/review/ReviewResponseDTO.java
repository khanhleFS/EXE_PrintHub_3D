package com.fpt.printhub_3d.dto.review;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ReviewResponseDTO(
        Long id,
        UUID orderId,
        UUID reviewerId,
        String reviewerName,
        UUID sellerId,
        String sellerName,
        Short rating,
        String comment,
        Instant createdAt
) {
}
