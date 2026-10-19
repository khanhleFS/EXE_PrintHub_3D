package com.fpt.printhub_3d.dto.checkout;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Yêu cầu tạo đơn cha từ giỏ hàng")
public record CheckoutOrderCreateRequestDTO(
        @Schema(description = "Danh sách chi tiết dòng sản phẩm gồm lineId, số lượng và revision")
        List<CheckoutLineItemRequestDTO> items,

        @Schema(description = "Danh sách clientLineId hoặc ID các dòng được chọn từ giỏ hàng (tương thích ngược)")
        List<String> selectedLineIds,

        @Schema(description = "Phương thức giao hàng: COMMON (Giao chung) hoặc SEPARATE (Giao riêng)", example = "COMMON")
        String shippingMode,

        @NotBlank(message = "Địa chỉ nhận hàng không được để trống")
        @Schema(description = "Địa chỉ nhận hàng đầy đủ")
        String shippingAddress,

        @Schema(description = "Tên người nhận hàng")
        String recipientName,

        @Schema(description = "Số điện thoại người nhận hàng")
        String recipientPhone,

        @Schema(description = "Phương thức thanh toán mong muốn: PAYOS hoặc COD", example = "PAYOS")
        String paymentMethod,

        @Schema(description = "Khóa chống trùng lặp gửi request")
        String idempotencyKey
) {}
