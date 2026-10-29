package com.fpt.printhub_3d.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Thông tin hành động bị chặn và lý do")
public record BlockedActionDTO(
        @Schema(description = "Tên hành động bị chặn (ví dụ: START_PREPARATION, START_PRINTING)")
        String action,

        @Schema(description = "Mã lý do (ví dụ: PAYMENT_REQUIRED, INVALID_TRANSITION)")
        String reasonCode,

        @Schema(description = "Thông điệp giải thích cho người dùng")
        String message
) {}
