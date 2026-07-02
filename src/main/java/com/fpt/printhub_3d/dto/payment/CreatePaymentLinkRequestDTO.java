package com.fpt.printhub_3d.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

@Schema(description = "Yêu cầu tạo link thanh toán PayOS")
public record CreatePaymentLinkRequestDTO(
        @NotNull(message = "ID đơn hàng không được để trống.")
        @Schema(description = "ID của đơn hàng cần thanh toán", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID orderId,

        @NotBlank(message = "Loại đơn hàng không được để trống.")
        @Pattern(regexp = "^(ORDER|CUSTOM_ORDER)$", message = "Loại đơn hàng phải là ORDER hoặc CUSTOM_ORDER.")
        @Schema(description = "Loại đơn hàng: ORDER (đơn thường) hoặc CUSTOM_ORDER (đơn in theo yêu cầu)", example = "ORDER")
        String orderType,

        @Schema(description = "Mô tả bổ sung cho giao dịch thanh toán", example = "Thanh toán đơn hàng in 3D")
        String description
) {
}
