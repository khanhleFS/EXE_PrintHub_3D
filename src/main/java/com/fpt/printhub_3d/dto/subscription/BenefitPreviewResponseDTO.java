package com.fpt.printhub_3d.dto.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenefitPreviewResponseDTO {

    @Schema(description = "Người dùng có gói hội viên đang hoạt động hay không")
    private boolean hasActiveSubscription;

    @Schema(description = "ID gói hội viên áp dụng chính")
    private UUID userSubscriptionId;

    @Schema(description = "Tên gói hội viên chính hoặc đang kích hoạt")
    private String planName;

    @Schema(description = "Tên gói áp dụng giảm giá đơn hàng")
    private String appliedDiscountPlanName;

    @Schema(description = "Tên gói áp dụng miễn phí vận chuyển")
    private String appliedShippingPlanName;

    @Schema(description = "Phần trăm giảm giá (%)")
    private int discountPercent;

    @Schema(description = "Tổng tiền hàng gốc (VND)")
    private BigDecimal itemsSubtotal;

    @Schema(description = "Số tiền được giảm từ gói hội viên (VND)")
    private BigDecimal discountAmount;

    @Schema(description = "Phí vận chuyển gốc (VND)")
    private BigDecimal baseShippingFee;

    @Schema(description = "Số tiền phí vận chuyển được miễn/giảm (VND)")
    private BigDecimal shippingDiscountAmount;

    @Schema(description = "Phí vận chuyển cuối cùng sau giảm (VND)")
    private BigDecimal finalShippingFee;

    @Schema(description = "Giới hạn lượt miễn phí ship trong kỳ (null nếu không giới hạn)")
    private Integer freeShippingUsesLimit;

    @Schema(description = "Số lượt miễn phí ship đã dùng trong kỳ")
    private int freeShippingUsesUsed;

    @Schema(description = "Số lượt miễn phí ship còn lại trong kỳ (null nếu không giới hạn)")
    private Integer remainingFreeShippingUses;

    @Schema(description = "Ưu tiên xử lý/in ấn hay không")
    private boolean priorityProduction;

    @Schema(description = "Tổng thanh toán cuối cùng dự kiến (VND)")
    private BigDecimal finalTotal;

    @Schema(description = "Lý do nếu chưa áp dụng được ưu đãi (ví dụ chưa đạt giá trị tối thiểu hoặc hết lượt)")
    private String ineligibleReason;
}

