package com.fpt.printhub_3d.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin chi tiết đánh giá sản phẩm")
public record ProductReviewResponseDTO(
        @Schema(description = "ID đánh giá")
        Long id,

        @Schema(description = "ID sản phẩm")
        UUID productId,

        @Schema(description = "Tên sản phẩm")
        String productTitle,

        @Schema(description = "ID đơn hàng")
        UUID orderId,

        @Schema(description = "ID chi tiết mục đơn hàng")
        Long orderItemId,

        @Schema(description = "ID người đánh giá")
        UUID reviewerId,

        @Schema(description = "Tên người đánh giá")
        String reviewerName,

        @Schema(description = "Ảnh đại diện người đánh giá")
        String reviewerAvatar,

        @Schema(description = "Số sao đánh giá (1 - 5)")
        Short rating,

        @Schema(description = "Nội dung nhận xét")
        String comment,

        @Schema(description = "Danh sách ảnh thành phẩm in 3D thực tế")
        List<String> imageUrls,

        @Schema(description = "Màu sắc thước đã đặt")
        String color,

        @Schema(description = "Khắc tên MSSV đã đặt")
        String engravingText,

        @Schema(description = "Thời gian gửi đánh giá")
        Instant createdAt,

        @Schema(description = "Thời gian cập nhật đánh giá gần nhất")
        Instant updatedAt
) {
}
