package com.fpt.printhub_3d.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Yêu cầu cập nhật giỏ hàng")
public record CartUpdateRequestDTO(
        @NotNull(message = "Danh sách sản phẩm không được null")
        @Size(max = 100, message = "Giỏ hàng tối đa 100 loại sản phẩm")
        @Schema(description = "Danh sách sản phẩm")
        List<@Valid CartItemRequestDTO> items
) {}
