package com.fpt.printhub_3d.dto.marketplace;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record UpdateProductRequestDTO(
        Long categoryId,

        @Size(max = 200, message = "Tiêu đề không quá 200 ký tự")
        String title,

        String description,

        @DecimalMin(value = "0.0", inclusive = false, message = "Giá sản phẩm phải lớn hơn 0")
        BigDecimal price,

        @Min(value = 0, message = "Số lượng tồn kho không được âm")
        Integer stock,

        String type, // PHYSICAL hoặc DIGITAL

        List<String> imageUrls
) {}
