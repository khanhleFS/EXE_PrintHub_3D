package com.fpt.printhub_3d.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Thông tin sản phẩm đặt mua")
public record OrderItemRequestDTO(
        @NotNull(message = "ID sản phẩm không được để trống")
        @Schema(description = "ID của sản phẩm cần mua")
        UUID productId,

        @NotNull(message = "Số lượng mua không được để trống")
        @Min(value = 1, message = "Số lượng mua tối thiểu là 1")
        @Schema(description = "Số lượng cần mua", example = "2")
        Integer quantity
) {
}
