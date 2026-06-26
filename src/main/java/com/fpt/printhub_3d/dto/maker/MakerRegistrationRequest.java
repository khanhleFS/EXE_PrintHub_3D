package com.fpt.printhub_3d.dto.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MakerRegistrationRequest(
        @NotBlank(message = "Ảnh mặt trước CCCD không được để trống.")
        @Size(max = 500, message = "URL ảnh không được vượt quá 500 ký tự.")
        @Schema(example = "https://example.com/cccd_front.jpg")
        String cccdFrontImageUrl,

        @Size(max = 150, message = "Tên doanh nghiệp không được vượt quá 150 ký tự.")
        @Schema(example = "Xưởng In 3D ABC")
        String businessName,

        @Schema(example = "Máy in FDM Prusa i3, Resin Elegoo Saturn")
        String equipmentInfo,

        @Size(max = 500, message = "Portfolio URL không được vượt quá 500 ký tự.")
        @Schema(example = "https://portfolio.example.com")
        String portfolioUrl,

        @Schema(example = "Chuyên in các chi tiết kỹ thuật PLA, PETG độ chính xác cao.")
        String bio
) {
}
