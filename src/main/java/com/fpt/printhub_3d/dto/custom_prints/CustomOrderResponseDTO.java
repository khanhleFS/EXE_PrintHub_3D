package com.fpt.printhub_3d.dto.custom_prints;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin chi tiết yêu cầu gia công in custom")
public record CustomOrderResponseDTO(
        @Schema(description = "ID yêu cầu in custom")
        UUID id,

        @Schema(description = "ID người mua (Customer)")
        UUID buyerId,

        @Schema(description = "Tên người mua")
        String buyerName,

        @Schema(description = "ID người bán (Maker)")
        UUID makerId,

        @Schema(description = "Tên người bán")
        String makerName,

        @Schema(description = "Mô tả yêu cầu gia công")
        String requirements,

        @Schema(description = "Đường dẫn file thiết kế STL đã đính kèm")
        String attachmentUrl,

        @Schema(description = "Báo giá từ Maker (sẽ được cập nhật sau khi Maker báo giá)")
        BigDecimal quotedPrice,

        @Schema(description = "Trạng thái yêu cầu (ví dụ: REQUESTED)")
        String status,

        @Schema(description = "Thời gian tạo yêu cầu")
        Instant createdAt,

        @Schema(description = "Thời gian cập nhật")
        Instant updatedAt
) {
}
