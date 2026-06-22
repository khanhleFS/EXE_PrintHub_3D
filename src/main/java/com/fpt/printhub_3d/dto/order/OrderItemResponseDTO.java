package com.fpt.printhub_3d.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Schema(description = "Chi tiết mặt hàng trong đơn hàng")
public record OrderItemResponseDTO(
        @Schema(description = "ID của chi tiết mặt hàng")
        Long id,

        @Schema(description = "ID sản phẩm")
        UUID productId,

        @Schema(description = "Tiêu đề sản phẩm", example = "Cuộn nhựa in PLA Sunlu 1kg")
        String productTitle,

        @Schema(description = "Số lượng mua", example = "2")
        Integer quantity,

        @Schema(description = "Đơn giá sản phẩm tại thời điểm mua (VND)", example = "350000")
        BigDecimal unitPrice,

        @Schema(description = "Tổng tiền mặt hàng (VND)", example = "700000")
        BigDecimal subTotal
) {
}
