package com.fpt.printhub_3d.dto.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BlacklistRequestDTO(
        @NotBlank(message = "Số CCCD không được để trống.")
        @Pattern(regexp = "^[0-9]{12}$", message = "Số CCCD phải gồm 12 chữ số.")
        @Schema(example = "038095011037")
        String cccdNumber,

        @NotBlank(message = "Lý do không được để trống.")
        @Schema(example = "Vi phạm hợp đồng, không nộp phạt sau 14 ngày.")
        String reason
) {
}
