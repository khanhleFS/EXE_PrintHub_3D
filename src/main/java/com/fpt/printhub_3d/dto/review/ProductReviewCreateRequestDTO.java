package com.fpt.printhub_3d.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Thông tin gửi đánh giá chất lượng sản phẩm")
public record ProductReviewCreateRequestDTO(
        @NotNull(message = "Order Item ID không được để trống")
        @Schema(description = "ID dòng mặt hàng trong đơn hàng", example = "12")
        Long orderItemId,

        @NotNull(message = "Điểm đánh giá không được để trống")
        @Min(value = 1, message = "Điểm đánh giá tối thiểu là 1 sao")
        @Max(value = 5, message = "Điểm đánh giá tối đa là 5 sao")
        @Schema(description = "Số sao đánh giá (1 - 5)", example = "5")
        Short rating,

        @Size(max = 2000, message = "Nội dung nhận xét tối đa 2000 ký tự")
        @Schema(description = "Nhận xét chất lượng in và sản phẩm", example = "Thước in chuẩn xác, vạch khắc sắc nét, nhựa chắc chắn!")
        String comment,

        @Schema(description = "Danh sách link ảnh thành phẩm in 3D thực tế (tối đa 5 ảnh)")
        List<String> imageUrls
) {
}
