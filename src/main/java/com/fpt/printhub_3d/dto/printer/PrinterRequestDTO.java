package com.fpt.printhub_3d.dto.printer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
@Schema(description = "Yêu cầu tạo hoặc cập nhật máy in 3D trong xưởng")
public record PrinterRequestDTO(
        @NotBlank(message = "Tên máy in không được để trống")
        @Size(max = 100, message = "Tên máy tối đa 100 ký tự")
        @Schema(description = "Tên máy in", example = "Bambu Lab X1-Carbon #01")
        String name,

        @NotNull(message = "Loại công nghệ in không được để trống")
        @Pattern(regexp = "FDM|SLA|SLS", message = "Loại công nghệ in phải là FDM, SLA hoặc SLS")
        @Schema(description = "Công nghệ in", example = "FDM")
        String type,

        @NotNull(message = "Trạng thái máy in không được để trống")
        @Pattern(regexp = "IDLE|PRINTING|MAINTENANCE|OFFLINE", message = "Trạng thái phải là IDLE, PRINTING, MAINTENANCE hoặc OFFLINE")
        @Schema(description = "Trạng thái máy", example = "IDLE")
        String status,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        @Schema(description = "Ghi chú kỹ thuật")
        String note
) {}
