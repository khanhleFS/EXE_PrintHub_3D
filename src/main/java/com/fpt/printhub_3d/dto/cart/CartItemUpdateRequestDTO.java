package com.fpt.printhub_3d.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(description = "Yêu cầu cập nhật 1 dòng trong giỏ hàng")
public record CartItemUpdateRequestDTO(
        @Min(value = 1, message = "Số lượng tối thiểu là 1")
        @Max(value = 999, message = "Số lượng tối đa là 999")
        @Schema(description = "Số lượng sản phẩm", example = "1")
        Integer quantity,

        @Schema(description = "Dữ liệu cấu hình thiết kế custom 3D (dạng JSON)")
        String customDesign,

        @Schema(description = "Tên bản thiết kế custom")
        String customName,

        @Schema(description = "Giá tính toán của bản custom")
        BigDecimal customPrice,

        @Schema(description = "Ảnh thumbnail render 3D (base64 hoặc URL)")
        String thumbnailUrl,

        @Schema(description = "Số phiên bản hiện tại phía client để kiểm tra optimistic lock", example = "1")
        Long revision
) {}
