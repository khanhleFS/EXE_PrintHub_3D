package com.fpt.printhub_3d.dto.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Thông tin yêu cầu Admin phát gói hội viên")
public record GiftSubscriptionRequestDTO(
        @NotNull(message = "ID người dùng không được để trống")
        @Schema(description = "ID người dùng được tặng gói")
        UUID userId,

        @NotNull(message = "ID gói dịch vụ không được để trống")
        @Schema(description = "ID gói dịch vụ muốn tặng")
        UUID planId,

        @NotBlank(message = "Lý do tặng gói không được để trống")
        @Schema(description = "Lý do tặng gói", example = "Tặng quà tri ân nhân ngày đặc biệt")
        String reason
) {
}
