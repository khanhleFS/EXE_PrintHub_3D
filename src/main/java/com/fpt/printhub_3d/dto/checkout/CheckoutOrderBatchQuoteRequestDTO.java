package com.fpt.printhub_3d.dto.checkout;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Yêu cầu báo giá theo từng đơn vị sản phẩm cho toàn bộ đơn cha CheckoutOrder")
public record CheckoutOrderBatchQuoteRequestDTO(
        @NotEmpty(message = "Danh sách báo giá từng sản phẩm không được để trống")
        @Valid
        List<ItemQuote> items
) {
    @Builder
    public record ItemQuote(
            @NotNull(message = "ID đơn in custom không được để trống")
            UUID customOrderId,

            @NotNull(message = "Đơn giá sản phẩm không được để trống")
            @DecimalMin(value = "1", message = "Đơn giá sản phẩm tối thiểu là 1 VND")
            BigDecimal unitPrice,

            @Size(max = 500, message = "Ghi chú báo giá tối đa 500 ký tự")
            String note
    ) {}
}

