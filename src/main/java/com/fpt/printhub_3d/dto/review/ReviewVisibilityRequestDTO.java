package com.fpt.printhub_3d.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Yêu cầu bật/tắt trạng thái công khai bài đánh giá")
public record ReviewVisibilityRequestDTO(
        @NotNull(message = "Trạng thái công khai (isPublic) không được để trống")
        @Schema(description = "true để hiển thị công khai trên trang chủ, false để ẩn")
        Boolean isPublic
) {
}

