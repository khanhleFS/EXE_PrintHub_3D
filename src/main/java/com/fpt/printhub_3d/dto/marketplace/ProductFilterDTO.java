package com.fpt.printhub_3d.dto.marketplace;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "Bộ lọc danh sách sản phẩm marketplace")
public record ProductFilterDTO(
        @Schema(description = "Từ khóa tìm kiếm (tiêu đề, mô tả, người bán)", example = "Nhựa in PLA")
        String keyword,

        @Schema(description = "ID của danh mục sản phẩm", example = "1")
        Long categoryId,

        @Schema(description = "Loại sản phẩm (PHYSICAL, DIGITAL)", example = "PHYSICAL")
        String type,

        @Schema(description = "Giá tối thiểu", example = "10000")
        @Min(0)
        Double minPrice,

        @Schema(description = "Giá tối đa", example = "2000000")
        @Min(0)
        Double maxPrice,

        @Schema(description = "Sắp xếp theo trường (price, createdAt, title)", example = "createdAt")
        String sortBy,

        @Schema(description = "Thứ tự sắp xếp (asc/desc)", example = "desc")
        String sortDirection,

        @Schema(description = "Số trang (bắt đầu từ 0)", example = "0")
        @Min(0)
        Integer page,

        @Schema(description = "Kích thước trang", example = "10")
        @Min(1)
        Integer size
) {
}
