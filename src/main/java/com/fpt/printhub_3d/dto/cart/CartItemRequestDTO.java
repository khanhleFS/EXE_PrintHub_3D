package com.fpt.printhub_3d.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Thông tin dòng sản phẩm trong giỏ hàng")
public record CartItemRequestDTO(
        @Schema(description = "ID sản phẩm (đối với sản phẩm catalog thông thường)")
        UUID productId,

        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng tối thiểu là 1")
        @Max(value = 999, message = "Số lượng tối đa là 999")
        @Schema(description = "Số lượng sản phẩm", example = "2")
        Integer quantity,

        @Schema(description = "Dữ liệu cấu hình thiết kế custom 3D (dạng JSON)")
        String customDesign,

        @Schema(description = "Tên bản thiết kế custom")
        String customName,

        @Schema(description = "Giá tính toán của bản custom")
        java.math.BigDecimal customPrice,

        @Schema(description = "Ảnh thumbnail render 3D (base64 hoặc URL)")
        String thumbnailUrl,

        @Schema(description = "Client line ID duy nhất")
        UUID clientLineId,

        @Schema(description = "Số revision của dòng")
        Long revision
) {}

