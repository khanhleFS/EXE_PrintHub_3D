package com.fpt.printhub_3d.dto.review;

import java.time.Instant;
import java.util.List;

public record FeaturedProductReviewDTO(
        Long id,
        String productTitle,
        String reviewerName,
        Short rating,
        String comment,
        List<String> imageUrls,
        Instant createdAt
) {
}
