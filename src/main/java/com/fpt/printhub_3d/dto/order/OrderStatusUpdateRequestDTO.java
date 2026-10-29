package com.fpt.printhub_3d.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Yêu cầu cập nhật trạng thái đơn hàng")
public record OrderStatusUpdateRequestDTO(
        @Schema(description = "Trạng thái tiếp theo (ví dụ: CANCELLED, PREPARING, SHIPPING, COMPLETED)", example = "PREPARING")
        String status,

        @Schema(description = "Hành động theo state machine (START_PREPARATION, START_PRINTING, START_SHIPPING, COMPLETE_DELIVERY, CANCEL)")
        String action,

        @Schema(description = "Khóa phân biệt yêu cầu tránh trùng lặp")
        String idempotencyKey,

        @Schema(description = "Phương thức thanh toán nếu có cập nhật")
        String paymentMethod
) {
    public String getEffectiveTarget() {
        if (action != null && !action.isBlank()) {
            return action;
        }
        return status;
    }
}
