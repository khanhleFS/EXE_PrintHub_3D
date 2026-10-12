package com.fpt.printhub_3d.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Tóm tắt điểm số và thống kê đánh giá của sản phẩm")
public record ProductReviewSummaryDTO(
        @Schema(description = "ID sản phẩm")
        UUID productId,

        @Schema(description = "Điểm sao trung bình (1.0 - 5.0)", example = "4.8")
        Double averageRating,

        @Schema(description = "Tổng số lượt đánh giá", example = "35")
        Integer reviewCount,

        @Schema(description = "Số lượng 5 sao")
        Long count5Star,

        @Schema(description = "Số lượng 4 sao")
        Long count4Star,

        @Schema(description = "Số lượng 3 sao")
        Long count3Star,

        @Schema(description = "Số lượng 2 sao")
        Long count2Star,

        @Schema(description = "Số lượng 1 sao")
        Long count1Star
) {
}
