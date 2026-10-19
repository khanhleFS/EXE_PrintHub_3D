package com.fpt.printhub_3d.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Yêu cầu hợp nhất giỏ hàng khách vãng lai (Guest) vào tài khoản đăng nhập")
public record CartMergeGuestRequestDTO(
        @NotNull(message = "mergeBatchId không được để trống")
        @Schema(description = "Mã định danh duy nhất của đợt merge từ client (UUID) dùng cho idempotency", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID mergeBatchId,

        @NotEmpty(message = "Danh sách sản phẩm guest cần merge không được để trống")
        @Schema(description = "Danh sách các món hàng vãng lai cần hợp nhất")
        List<@Valid CartItemRequestDTO> items
) {}
