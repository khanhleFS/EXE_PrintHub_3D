package com.fpt.printhub_3d.dto.maker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MakerStatusUpdateRequest(
        @NotBlank(message = "Trạng thái không được để trống.")
        @Pattern(regexp = "APPROVED|REJECTED", message = "Trạng thái chỉ được phép là APPROVED hoặc REJECTED.")
        @Schema(example = "APPROVED")
        String status
) {
}
