package com.fpt.printhub_3d.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Đánh giá đơn in 3D Custom hiển thị trên trang quản trị Admin")
public record AdminCustomOrderReviewDTO(
        Long id,
        UUID customOrderId,
        UUID checkoutOrderId,
        String rulerModel,
        Integer quantity,
        String color,
        String customName,
        UUID reviewerId,
        String reviewerName,
        Short rating,
        String comment,
        List<String> imageUrls,
        Instant createdAt,
        Instant updatedAt
) {
}
