package com.fpt.printhub_3d.dto.dispute;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Thông tin khởi tạo hồ sơ khiếu nại/tranh chấp")
public record DisputeCreateRequestDTO(
        @NotNull(message = "Order ID không được để trống")
        UUID orderId,

        @NotBlank(message = "Mô tả khiếu nại không được để trống")
        String description,

        @Size(max = 500, message = "Evidence URL tối đa 500 ký tự")
        String evidenceUrl
) {
}
