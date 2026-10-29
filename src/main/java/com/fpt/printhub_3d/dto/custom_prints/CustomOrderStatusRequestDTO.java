package com.fpt.printhub_3d.dto.custom_prints;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Yêu cầu cập nhật trạng thái đơn in custom")
public record CustomOrderStatusRequestDTO(
        @Schema(description = "Trạng thái tiếp theo (ACCEPTED, PREPARING, PRINTING, SHIPPING, COMPLETED, CANCELLED)", example = "ACCEPTED")
        String status,

        @Schema(description = "Hành động theo state machine (START_PREPARATION, START_PRINTING, START_SHIPPING, COMPLETE_DELIVERY, CANCEL)")
        String action,

        @Schema(description = "Khóa phân biệt yêu cầu tránh trùng lặp")
        String idempotencyKey,

        @Schema(description = "Phương thức thanh toán nếu chấp nhận báo giá (COD, PAYOS)", example = "COD")
        String paymentMethod
) {
    public String getEffectiveTarget() {
        if (action != null && !action.isBlank()) {
            return action;
        }
        return status;
    }
}
