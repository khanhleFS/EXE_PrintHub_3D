package com.fpt.printhub_3d.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Thông tin giao hàng của đơn hàng")
public record ShippingInfoResponseDTO(
        @Schema(description = "Tên người nhận", example = "Nguyễn Văn A")
        String recipientName,

        @Schema(description = "Số điện thoại", example = "0987654321")
        String phone,

        @Schema(description = "Địa chỉ chi tiết", example = "Số 123 Đường ABC, Phường 4, Quận 5")
        String address,

        @Schema(description = "Tỉnh/Thành phố", example = "TP. Hồ Chí Minh")
        String province,

        @Schema(description = "Mã vận đơn", example = "GHN123456789")
        String trackingNumber
) {
}
