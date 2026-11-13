package com.fpt.printhub_3d.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Kết quả cập nhật trạng thái công khai bài đánh giá")
public record ReviewVisibilityResponseDTO(
        ProductReviewResponseDTO review,
        long publicCount,
        int maxPublicLimit
) {
}

