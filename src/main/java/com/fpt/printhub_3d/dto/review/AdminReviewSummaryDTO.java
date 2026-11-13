package com.fpt.printhub_3d.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Thống kê tổng quan đánh giá cho Admin")
public record AdminReviewSummaryDTO(
        long totalReviews,
        long publicCount,
        long hiddenCount,
        int maxPublicLimit
) {
}

