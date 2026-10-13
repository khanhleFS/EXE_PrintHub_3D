package com.fpt.printhub_3d.dto.checkout;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
@Schema(description = "Yêu cầu xác nhận đơn cha sau khi đã có báo giá")
public record CheckoutOrderConfirmRequestDTO(
        @NotNull(message = "Phiên bản đơn hàng không được để trống")
        @Schema(description = "summaryVersion hiện tại của đơn hàng để chống xung đột", example = "1")
        Long summaryVersion,

        @Schema(description = "Phương thức thanh toán: PAYOS hoặc COD", example = "PAYOS")
        String paymentMethod
) {}
