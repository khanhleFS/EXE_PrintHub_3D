package com.fpt.printhub_3d.dto.custom_prints;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Yêu cầu đặt in custom 3D từ file đã tải lên")
public record CustomOrderCreateRequestDTO(
        @Schema(description = "ID tệp trong File Vault (nếu có)")
        UUID fileId,

        @Schema(description = "URL CDN tệp 3D từ Cloudinary (nếu upload trực tiếp)")
        String attachmentUrl,

        @NotBlank(message = "Mô tả yêu cầu không được để trống")
        @Size(max = 4000, message = "Yêu cầu tối đa 4000 ký tự")
        @Schema(description = "Yêu cầu in chi tiết (vật liệu, màu, infill...)")
        String requirements,

        @Min(value = 1, message = "Số lượng tối thiểu là 1")
        @Schema(description = "Số lượng in", example = "1")
        int quantity,

        @NotBlank(message = "Địa chỉ nhận hàng không được để trống")
        @Size(max = 1000, message = "Địa chỉ tối đa 1000 ký tự")
        @Schema(description = "Thông tin nhận hàng (tên, sđt, địa chỉ)")
        String shippingAddress,

        @Size(max = 100)
        String rulerModel,

        @Size(max = 100)
        String customName,

        @Size(max = 50)
        String customStudentId,

        @Size(max = 50)
        String color,

        @Size(max = 50)
        String fontStyle,

        @Size(max = 20)
        String paymentMethod
) {}
