package com.fpt.printhub_3d.dto.marketplace;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateProductRequestDTO(
        @NotNull(message = "ID danh mục không được để trống")
        Long categoryId,

        @NotBlank(message = "Tiêu đề sản phẩm không được để trống")
        @Size(max = 200, message = "Tiêu đề không quá 200 ký tự")
        String title,

        String description,

        @NotNull(message = "Giá sản phẩm không được để trống")
        @DecimalMin(value = "0.0", inclusive = false, message = "Giá sản phẩm phải lớn hơn 0")
        BigDecimal price,

        @NotNull(message = "Số lượng tồn kho không được để trống")
        @Min(value = 0, message = "Số lượng tồn kho không được âm")
        Integer stock,

        @NotBlank(message = "Loại sản phẩm không được để trống")
        String type, // PHYSICAL hoặc DIGITAL

        List<String> imageUrls
) {}
