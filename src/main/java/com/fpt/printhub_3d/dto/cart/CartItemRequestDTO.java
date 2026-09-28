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
        @NotNull(message = "ID sản phẩm không được để trống")
        @Schema(description = "ID sản phẩm")
        UUID productId,

        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng tối thiểu là 1")
        @Max(value = 999, message = "Số lượng tối đa là 999")
        @Schema(description = "Số lượng sản phẩm", example = "2")
        Integer quantity
) {}
