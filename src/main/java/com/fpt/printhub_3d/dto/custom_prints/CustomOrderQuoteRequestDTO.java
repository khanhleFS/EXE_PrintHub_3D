package com.fpt.printhub_3d.dto.custom_prints;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(description = "Yêu cầu báo giá từ Admin cho đơn in custom")
public record CustomOrderQuoteRequestDTO(
        @NotNull(message = "Giá báo không được để trống")
        @DecimalMin(value = "1", message = "Giá báo tối thiểu là 1 VND")
        @Schema(description = "Giá báo (VND)", example = "150000")
        BigDecimal price
) {}
