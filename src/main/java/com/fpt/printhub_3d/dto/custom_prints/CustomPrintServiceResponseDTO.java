package com.fpt.printhub_3d.dto.custom_prints;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Response body for a custom print service")
public record CustomPrintServiceResponseDTO(

        @Schema(description = "ID dịch vụ")
        UUID id,

        @Schema(description = "ID của maker sở hữu")
        UUID makerId,

        @Schema(description = "Tên maker", example = "Nguyễn Văn A")
        String makerName,

        @Schema(description = "Tên dịch vụ", example = "High Precision FDM Printing")
        String serviceName,

        @Schema(description = "Mô tả dịch vụ", example = "Professional 3D printing service")
        String description,

        @Schema(description = "Danh sách máy in", example = "[\"Bambu Lab X1 Carbon\"]")
        List<String> printerModels,

        @Schema(description = "Vật liệu hỗ trợ", example = "[\"PLA\", \"PETG\", \"ABS\"]")
        List<String> supportedMaterials,

        @Schema(description = "Đơn giá tối thiểu (VND)", example = "100000")
        BigDecimal minimumPrice,

        @Schema(description = "Kích thước in tối đa", example = "300x300x300 mm")
        String maxPrintSize,

        @Schema(description = "Số ngày sản xuất ước tính", example = "3")
        Integer estimatedProductionDays,

        @Schema(description = "Thời gian tạo")
        LocalDateTime createdAt,

        @Schema(description = "Thời gian cập nhật cuối")
        LocalDateTime updatedAt
) {
}
