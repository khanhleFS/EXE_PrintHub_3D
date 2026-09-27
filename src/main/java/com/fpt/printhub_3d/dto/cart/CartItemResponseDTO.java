package com.fpt.printhub_3d.dto.cart;

import com.fpt.printhub_3d.dto.marketplace.ProductResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Chi tiết một dòng sản phẩm trong giỏ hàng")
public record CartItemResponseDTO(
        @Schema(description = "ID dòng giỏ hàng")
        Long id,

        @Schema(description = "Số lượng đặt")
        Integer quantity,

        @Schema(description = "Thông tin chi tiết sản phẩm")
        ProductResponseDTO product
) {}
