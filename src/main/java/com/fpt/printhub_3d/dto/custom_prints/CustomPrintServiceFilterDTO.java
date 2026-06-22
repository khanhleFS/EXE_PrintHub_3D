package com.fpt.printhub_3d.dto.custom_prints;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "Bộ lọc tìm kiếm danh sách dịch vụ in 3D")
public record CustomPrintServiceFilterDTO(

        @Schema(description = "Từ khóa tìm kiếm (tên dịch vụ, mô tả, tên maker)", example = "PLA")
        String keyword,

        @Schema(description = "Lọc theo vật liệu hỗ trợ", example = "PLA")
        String material,

        @Schema(description = "Giá tối thiểu (VND)", example = "50000")
        @Min(0)
        Double minPrice,

        @Schema(description = "Giá tối đa (VND)", example = "500000")
        @Min(0)
        Double maxPrice,

        @Schema(description = "Sắp xếp theo trường (minimumPrice, estimatedProductionDays, createdAt)", example = "createdAt")
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
