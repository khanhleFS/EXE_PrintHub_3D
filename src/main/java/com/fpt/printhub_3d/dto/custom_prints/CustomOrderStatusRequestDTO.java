package com.fpt.printhub_3d.dto.custom_prints;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
@Schema(description = "Yêu cầu cập nhật trạng thái đơn in custom")
public record CustomOrderStatusRequestDTO(
        @Schema(description = "Trạng thái tiếp theo (ACCEPTED, PAID, PREPARING, PRINTING, SHIPPING, COMPLETED, CANCELLED)", example = "ACCEPTED")
        String status,

        @Schema(description = "Hành động theo state machine (START_PREPARATION, START_PRINTING, START_SHIPPING, COMPLETE_DELIVERY, CANCEL)")
        String action,

        @Schema(description = "Khóa phân biệt yêu cầu tránh trùng lặp")
        String idempotencyKey,

        @Schema(description = "Phương thức thanh toán nếu chấp nhận báo giá (COD, PAYOS)", example = "COD")
        String paymentMethod,

        @Schema(description = "Lý do hủy yêu cầu báo giá / đơn in custom")
        @Size(max = 1000)
        String cancelReason,

        @Schema(description = "Trường dự phòng tương thích cũ cho cancelReason")
        @Size(max = 1000)
        String reason
) {
    public CustomOrderStatusRequestDTO(String status, String action, String idempotencyKey, String paymentMethod) {
        this(status, action, idempotencyKey, paymentMethod, null, null);
    }

    public String getEffectiveTarget() {
        if (action != null && !action.isBlank()) {
            return action;
        }
        return status;
    }

    public String getEffectiveCancelReason() {
        if (cancelReason != null && !cancelReason.isBlank()) {
            return cancelReason.trim();
        }
        if (reason != null && !reason.isBlank()) {
            return reason.trim();
        }
        return null;
    }
}
