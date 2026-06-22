package com.fpt.printhub_3d.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin chi tiết đơn hàng sau khi tạo")
public record OrderResponseDTO(
        @Schema(description = "ID đơn hàng")
        UUID id,

        @Schema(description = "ID người mua")
        UUID buyerId,

        @Schema(description = "Tên người mua")
        String buyerName,

        @Schema(description = "ID người bán (Maker)")
        UUID sellerId,

        @Schema(description = "Tên người bán (Maker)")
        String sellerName,

        @Schema(description = "Tổng giá trị đơn hàng (VND)", example = "700000")
        BigDecimal totalAmount,

        @Schema(description = "Phí hoa hồng sàn thu (VND)", example = "35000")
        BigDecimal commissionFee,

        @Schema(description = "Trạng thái đơn hàng", example = "PENDING")
        String status,

        @Schema(description = "Thông tin người nhận hàng")
        ShippingInfoResponseDTO shippingInfo,

        @Schema(description = "Danh sách sản phẩm trong đơn hàng")
        List<OrderItemResponseDTO> items,

        @Schema(description = "Thời gian tạo đơn hàng")
        Instant createdAt,

        @Schema(description = "Thời gian cập nhật")
        Instant updatedAt
) {
}
