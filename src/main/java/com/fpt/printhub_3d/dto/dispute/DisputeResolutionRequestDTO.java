package com.fpt.printhub_3d.dto.dispute;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Thông tin phán quyết xử lý dispute của Admin")
public record DisputeResolutionRequestDTO(
        @NotBlank(message = "Trạng thái phán quyết không được để trống")
        @Schema(description = "RESOLVED hoặc REJECTED", example = "RESOLVED")
        String status,

        @DecimalMin(value = "0.0", inclusive = true, message = "Số tiền hoàn phải lớn hơn hoặc bằng 0")
        BigDecimal refundAmount,

        @Size(max = 20, message = "Loại hoàn tiền tối đa 20 ký tự")
        @Schema(description = "FULL hoặc PARTIAL", example = "PARTIAL")
        String refundType,

        @NotBlank(message = "Nội dung phán quyết không được để trống")
        String resolutionNote
) {
}
