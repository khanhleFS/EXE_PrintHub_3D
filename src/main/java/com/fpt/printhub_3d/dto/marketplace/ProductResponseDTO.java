package com.fpt.printhub_3d.dto.marketplace;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin chi tiết sản phẩm trên marketplace")
public record ProductResponseDTO(
        @Schema(description = "ID sản phẩm")
        UUID id,

        @Schema(description = "ID người bán")
        UUID sellerId,

        @Schema(description = "Tên người bán", example = "Maker Hub")
        String sellerName,

        @Schema(description = "ID danh mục")
        Long categoryId,

        @Schema(description = "Tên danh mục", example = "Nguyên liệu in 3D")
        String categoryName,

        @Schema(description = "Tiêu đề sản phẩm", example = "Cuộn nhựa in PLA Sunlu 1kg")
        String title,

        @Schema(description = "Mô tả sản phẩm", example = "Nhựa in PLA thân thiện với môi trường, đường kính 1.75mm")
        String description,

        @Schema(description = "Giá bán (VND)", example = "350000")
        BigDecimal price,

        @Schema(description = "Số lượng tồn kho", example = "20")
        Integer stock,

        @Schema(description = "Loại sản phẩm (PHYSICAL, DIGITAL)", example = "PHYSICAL")
        String type,

        @Schema(description = "Trạng thái (ACTIVE, INACTIVE)", example = "ACTIVE")
        String status,

        @Schema(description = "Ảnh đại diện sản phẩm", example = "http://example.com/image.png")
        String primaryImageUrl,

        @Schema(description = "Danh sách tất cả ảnh sản phẩm")
        List<String> imageUrls,

        @Schema(description = "Thời gian tạo")
        Instant createdAt,

        @Schema(description = "Thời gian cập nhật")
        Instant updatedAt
) {
}
