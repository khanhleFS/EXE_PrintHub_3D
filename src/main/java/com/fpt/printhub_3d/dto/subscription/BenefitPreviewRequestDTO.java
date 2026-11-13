package com.fpt.printhub_3d.dto.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenefitPreviewRequestDTO {

    @Schema(description = "Phạm vi áp dụng: MARKETPLACE, CUSTOM hoặc MIXED", example = "CUSTOM")
    private String scope;

    @Schema(description = "Tổng tiền hàng (VND) chưa trừ giảm giá và chưa cộng phí ship", example = "200000")
    private BigDecimal itemsSubtotal;

    @Schema(description = "Phí vận chuyển gốc (VND)", example = "30000")
    private BigDecimal baseShippingFee;

    @Schema(description = "Phần tiền hàng CUSTOM (dùng khi scope = MIXED)", example = "120000")
    private BigDecimal customSubtotal;

    @Schema(description = "Phần tiền hàng MARKETPLACE (dùng khi scope = MIXED)", example = "80000")
    private BigDecimal marketplaceSubtotal;
}

