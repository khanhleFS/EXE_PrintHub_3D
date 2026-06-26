package com.fpt.printhub_3d.dto.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Thông tin tạo hoặc cập nhật gói dịch vụ thành viên/quảng cáo")
public record SubscriptionPlanRequestDTO(
        @NotBlank(message = "Tên gói không được để trống")
        @Size(max = 255, message = "Tên gói tối đa 255 ký tự")
        @Schema(description = "Tên gói", example = "Customer VIP Silver")
        String name,

        @NotNull(message = "Giá gói không được để trống")
        @DecimalMin(value = "0.0", inclusive = true, message = "Giá gói phải lớn hơn hoặc bằng 0")
        @Schema(description = "Giá mua gói", example = "99000")
        BigDecimal price,

        @NotBlank(message = "Đặc quyền gói không được để trống")
        @Size(max = 2000, message = "Đặc quyền tối đa 2000 ký tự")
        @Schema(description = "Mô tả đặc quyền ưu đãi/marketing", example = "Giảm 10% đơn hàng, miễn phí ship 2 đơn/tháng")
        String benefits,

        @Min(value = 0, message = "Điểm quy đổi tối thiểu phải lớn hơn hoặc bằng 0")
        @Schema(description = "Số điểm tối thiểu cần để đổi gói Customer. Gói Maker có thể bỏ trống.", example = "500")
        Integer requiredPoints
) {
}
