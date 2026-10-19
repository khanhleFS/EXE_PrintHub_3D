package com.fpt.printhub_3d.dto.checkout;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
@Schema(description = "Thông tin dòng sản phẩm được chọn khi tạo đơn thanh toán")
public record CheckoutLineItemRequestDTO(
        @NotNull(message = "lineId không được để trống")
        @Schema(description = "clientLineId hoặc ID của dòng trong giỏ hàng")
        String lineId,

        @Schema(description = "Số lượng sản phẩm")
        Integer quantity,

        @Schema(description = "Số revision của dòng tại thời điểm chọn")
        Long revision
) {}
