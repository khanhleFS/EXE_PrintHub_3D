package com.fpt.printhub_3d.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Yêu cầu khởi tạo đơn hàng")
public record OrderCreateRequestDTO(
        @NotBlank(message = "Tên người nhận không được để trống")
        @Size(max = 150, message = "Tên người nhận không quá 150 ký tự")
        @Schema(description = "Tên người nhận hàng", example = "Nguyễn Văn A")
        String recipientName,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Size(max = 20, message = "Số điện thoại không quá 20 ký tự")
        @Schema(description = "Số điện thoại người nhận", example = "0987654321")
        String phone,

        @NotBlank(message = "Địa chỉ nhận hàng không được để trống")
        @Size(max = 255, message = "Địa chỉ không quá 255 ký tự")
        @Schema(description = "Địa chỉ nhận hàng (Số nhà, đường, phường/xã, quận/huyện)", example = "Số 123 Đường ABC, Phường 4, Quận 5")
        String address,

        @NotBlank(message = "Tỉnh/Thành phố không được để trống")
        @Size(max = 100, message = "Tỉnh/Thành phố không quá 100 ký tự")
        @Schema(description = "Tỉnh/Thành phố nhận hàng", example = "TP. Hồ Chí Minh")
        String province,

        @NotEmpty(message = "Danh sách sản phẩm mua hàng không được để trống")
        @Valid
        @Schema(description = "Danh sách các sản phẩm và số lượng tương ứng")
        List<OrderItemRequestDTO> items
) {
}
