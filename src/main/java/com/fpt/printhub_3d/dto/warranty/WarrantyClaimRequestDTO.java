package com.fpt.printhub_3d.dto.warranty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Yêu cầu bảo hành / khiếu nại sản phẩm")
public record WarrantyClaimRequestDTO(
        @NotNull(message = "ID đơn hàng không được để trống")
        @Schema(description = "ID đơn hàng")
        UUID orderId,

        @NotBlank(message = "Lý do bảo hành không được để trống")
        @Size(max = 4000, message = "Lý do tối đa 4000 ký tự")
        @Schema(description = "Lý do / mô tả lỗi chi tiết")
        String description,

        @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự")
        @Schema(description = "Hình ảnh minh chứng lỗi")
        String imageUrl
) {}
