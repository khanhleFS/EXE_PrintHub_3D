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

        @Schema(description = "ID người tiếp nhận/xử lý (Admin)")
        UUID processedById,

        @Schema(description = "Tên người tiếp nhận/xử lý (Admin)")
        String processedByName,

        @Schema(description = "Mô tả yêu cầu gia công")
        String requirements,

        @Schema(description = "Đường dẫn file thiết kế STL đã đính kèm")
        String attachmentUrl,

        @Schema(description = "Đơn giá / 1 chiếc do xưởng báo (VND)")
        BigDecimal unitQuotedPrice,

        @Schema(description = "Thành tiền line item = đơn giá × số lượng (VND)")
        BigDecimal quotedPrice,

        @Schema(description = "Ghi chú báo giá / kỹ thuật từ xưởng")
        String quoteNote,

        @Schema(description = "Thời điểm Admin báo giá gần nhất")
        Instant quotedAt,

        @Schema(description = "ID đơn cha CheckoutOrder nếu đặt qua giỏ nhiều món")
        java.util.UUID checkoutOrderId,

        @Schema(description = "Trạng thái yêu cầu (ví dụ: REQUESTED)")
        String status,

        @Schema(description = "Thời gian tạo yêu cầu")
        Instant createdAt,

        @Schema(description = "Thời gian cập nhật")
        Instant updatedAt,

        @Schema(description = "Tiền hàng tạm tính (VND)")
        BigDecimal subtotalAmount,

        @Schema(description = "Phí vận chuyển gốc (VND)")
        BigDecimal shippingFee,

        @Schema(description = "Số tiền giảm từ gói hội viên (VND)")
        BigDecimal discountAmount,

        @Schema(description = "Số tiền phí ship được miễn/giảm từ gói hội viên (VND)")
        BigDecimal shippingDiscountAmount,

        @Schema(description = "ID gói hội viên đã áp dụng")
        UUID appliedSubscriptionId,

        @Schema(description = "Tên gói hội viên đã áp dụng")
        String appliedSubscriptionName,

        @Schema(description = "Ưu tiên xử lý / in ấn")
        Boolean priorityProduction,

        @Schema(description = "Địa chỉ giao hàng")
        String shippingAddress,

        @Schema(description = "Lý do hủy yêu cầu")
        String cancelReason,

        @Schema(description = "Vai trò người hủy (ADMIN / BUYER)")
        String cancelledByRole,

        @Schema(description = "Thời điểm hủy yêu cầu")
        Instant cancelledAt
) {
}
