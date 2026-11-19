package com.fpt.printhub_3d.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Yêu cầu gửi hoặc chỉnh sửa đánh giá dịch vụ in 3D cho 1 dòng thiết kế custom")
public record CustomOrderReviewRequestDTO(
        @NotNull(message = "Điểm đánh giá không được để trống")
        @Min(value = 1, message = "Điểm đánh giá tối thiểu là 1 sao")
        @Max(value = 5, message = "Điểm đánh giá tối đa là 5 sao")
        @Schema(description = "Số sao nguyên từ 1 đến 5", example = "5")
        Short rating,

        @Size(max = 2000, message = "Nội dung nhận xét tối đa 2000 ký tự")
        @Schema(description = "Nhận xét chất lượng dịch vụ in 3D (tối đa 2000 ký tự)")
        String comment,

        @Size(max = 5, message = "Tối đa 5 hình ảnh đính kèm")
        @Schema(description = "Danh sách URL ảnh thành phẩm (tối đa 5 ảnh)")
        List<String> imageUrls
) {
}
