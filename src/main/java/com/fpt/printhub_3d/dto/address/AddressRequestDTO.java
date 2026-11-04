package com.fpt.printhub_3d.dto.address;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu thêm mới hoặc cập nhật địa chỉ giao hàng")
public class AddressRequestDTO {
    @Size(max = 150)
    private String name;

    @Size(max = 150)
    private String recipientName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(
            regexp = "^(0|\\+84|84)(3|5|7|8|9)[0-9]{8}$",
            message = "Số điện thoại không đúng định dạng di động Việt Nam (10 chữ số, đầu 03, 05, 07, 08, 09)"
    )
    @Size(max = 20)
    private String phone;

    @NotBlank(message = "Địa chỉ chi tiết không được để trống")
    @Size(max = 255)
    private String addressLine;

    @NotBlank(message = "Tỉnh/thành không được để trống")
    @Size(max = 100)
    private String province;

    private Boolean isDefault = false;

    public String resolveRecipientName() {
        if (recipientName != null && !recipientName.isBlank()) return recipientName;
        if (name != null && !name.isBlank()) return name;
        return "Người nhận";
    }
}
