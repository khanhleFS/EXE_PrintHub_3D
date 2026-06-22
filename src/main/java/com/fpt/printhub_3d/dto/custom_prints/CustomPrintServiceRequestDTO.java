package com.fpt.printhub_3d.dto.custom_prints;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Request body for creating a custom print service")
public record CustomPrintServiceRequestDTO(

        @NotBlank(message = "Tên dịch vụ không được để trống.")
        @Size(max = 200, message = "Tên dịch vụ không được vượt quá 200 ký tự.")
        @Schema(example = "High Precision FDM Printing")
        String serviceName,

        @NotBlank(message = "Mô tả dịch vụ không được để trống.")
        @Schema(example = "Professional 3D printing service")
        String description,

        @NotEmpty(message = "Danh sách máy in không được để trống.")
        @Schema(example = "[\"Bambu Lab X1 Carbon\"]")
        List<String> printerModels,

        @NotEmpty(message = "Danh sách vật liệu hỗ trợ không được để trống.")
        @Schema(example = "[\"PLA\", \"PETG\", \"ABS\"]")
        List<String> supportedMaterials,

        @NotNull(message = "Đơn giá tối thiểu không được để trống.")
        @Positive(message = "Đơn giá tối thiểu phải lớn hơn 0.")
        @Schema(example = "100000")
        BigDecimal minimumPrice,

        @NotBlank(message = "Kích thước in tối đa không được để trống.")
        @Size(max = 100, message = "Kích thước in tối đa không được vượt quá 100 ký tự.")
        @Schema(example = "300x300x300 mm")
        String maxPrintSize,

        @NotNull(message = "Số ngày sản xuất ước tính không được để trống.")
        @Positive(message = "Số ngày sản xuất ước tính phải lớn hơn 0.")
        @Schema(example = "3")
        Integer estimatedProductionDays
) {
}
