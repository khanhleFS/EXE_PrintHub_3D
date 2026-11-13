package com.fpt.printhub_3d.dto.checkout;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Yêu cầu cập nhật trạng thái đơn cha CheckoutOrder từ Admin")
public record AdminCheckoutOrderStatusRequestDTO(
        @Schema(description = "Trạng thái đích (PREPARING, PRINTING, SHIPPING, COMPLETED, CANCELLED)", example = "PREPARING")
        String status,

        @Schema(description = "Hành động chuyển trạng thái (START_PREPARATION, START_PRINTING, START_SHIPPING, COMPLETE_DELIVERY, CANCEL)")
        String action,

        @Schema(description = "Lý do hủy đơn khi chuyển sang CANCELLED")
        String cancelReason
) {
    public String getEffectiveTarget() {
        if (action != null && !action.isBlank()) {
            return action;
        }
        return status;
    }
}

