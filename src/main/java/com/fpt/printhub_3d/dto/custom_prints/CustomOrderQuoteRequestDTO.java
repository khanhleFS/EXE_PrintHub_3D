package com.fpt.printhub_3d.dto.custom_prints;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(description = "Yêu cầu báo giá từ Admin cho đơn in custom")
public record CustomOrderQuoteRequestDTO(
        @DecimalMin(value = "1", message = "Đơn giá sản phẩm tối thiểu là 1 VND")
        @Schema(description = "Đơn giá / 1 chiếc (VND)", example = "50000")
        BigDecimal unitPrice,

        @DecimalMin(value = "1", message = "Giá báo tối thiểu là 1 VND")
        @Schema(description = "Giá báo tương thích ngược (ưu tiên dùng unitPrice)", example = "50000")
        BigDecimal price,

        @jakarta.validation.constraints.Size(max = 500, message = "Ghi chú báo giá tối đa 500 ký tự")
        @Schema(description = "Ghi chú kỹ thuật / báo giá từ xưởng", example = "Vật liệu PLA+, đã tính hỗ trợ đỡ")
        String note
) {
    public CustomOrderQuoteRequestDTO(BigDecimal price) {
        this(price, price, null);
    }

    public CustomOrderQuoteRequestDTO(BigDecimal unitPrice, String note) {
        this(unitPrice, null, note);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public BigDecimal getEffectiveUnitPrice() {
        return unitPrice != null ? unitPrice : price;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "Đơn giá báo không được để trống và phải >= 1 VND")
    public boolean isUnitPricePresent() {
        BigDecimal effective = getEffectiveUnitPrice();
        return effective != null && effective.compareTo(BigDecimal.ONE) >= 0;
    }
}
