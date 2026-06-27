package com.fpt.printhub_3d.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Thông tin đánh giá Maker sau đơn hàng")
public record ReviewCreateRequestDTO(
        @NotNull(message = "Order ID không được để trống")
        UUID orderId,

        @NotNull(message = "Rating không được để trống")
        @Min(value = 1, message = "Rating tối thiểu là 1 sao")
        @Max(value = 5, message = "Rating tối đa là 5 sao")
        Short rating,

        @Size(max = 2000, message = "Nội dung đánh giá tối đa 2000 ký tự")
        String comment
) {
}
