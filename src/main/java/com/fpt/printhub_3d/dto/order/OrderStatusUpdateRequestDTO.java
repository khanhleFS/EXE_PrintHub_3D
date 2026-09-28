package com.fpt.printhub_3d.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
@Schema(description = "Yêu cầu cập nhật trạng thái đơn hàng")
public record OrderStatusUpdateRequestDTO(
        @NotBlank(message = "Trạng thái không được để trống")
        @Schema(description = "Trạng thái tiếp theo (ví dụ: CANCELLED, PRINTING, COMPLETED)", example = "CANCELLED")
        String status,

        @Schema(description = "Phương thức thanh toán nếu có cập nhật")
        String paymentMethod
) {}
