package com.fpt.printhub_3d.dto.custom_prints;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
@Schema(description = "Yêu cầu cập nhật trạng thái đơn in custom")
public record CustomOrderStatusRequestDTO(
        @NotBlank(message = "Trạng thái không được để trống")
        @Schema(description = "Trạng thái tiếp theo (ACCEPTED, PRINTING, COMPLETED, CANCELLED)", example = "ACCEPTED")
        String status,

        @Schema(description = "Phương thức thanh toán nếu chấp nhận báo giá (COD, PAYOS)", example = "COD")
        String paymentMethod
) {}
