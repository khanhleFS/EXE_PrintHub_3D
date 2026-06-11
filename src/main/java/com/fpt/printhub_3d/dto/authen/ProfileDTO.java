package com.fpt.printhub_3d.dto.authen;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record ProfileDTO(

        @NotBlank(message = "Họ tên không được để trống.")
        @Size(min = 9, max = 50, message = "Họ tên phải từ 2 đến 50 ký tự.")
        @Pattern(regexp = "^\\S.*\\S$|^\\S$", message = "Họ tên không được có khoảng trắng ở đầu hoặc cuối.")
        @Pattern(regexp = "^[\\p{L} ]+$", message = "Họ tên chỉ được chứa chữ cái và khoảng trắng.")
        @Pattern(regexp = "^(?!.*\\s{2,}).*$", message = "Họ tên không được có 2 khoảng trắng liên tiếp.")
        @Schema(example = "Nguyen Van A")
        String fullName,

        @NotBlank(message = "Email không được để trống.")
        @Email(message = "Email không đúng định dạng.")
        @Size(max = 100, message = "Email không được vượt quá 100 ký tự.")
        @Schema(example = "user@example.com")
        String email,

        @NotBlank(message = "Số điện thoại không được để trống.")
        @Pattern(
                regexp = "^(0[3|5|7|8|9])+([0-9]{8})$",
                message = "Số điện thoại không đúng định dạng (VD: 0912345678)."
        )
        @Schema(example = "0912345678")
        String phone,

        @Size(max = 200, message = "Địa chỉ không được vượt quá 200 ký tự.")
        @Pattern(regexp = "^(?!\\s)(?!.*\\s$).*$", message = "Địa chỉ không được có khoảng trắng ở đầu hoặc cuối.")
        @Schema(example = "123 Nguyen Trai, District 1, Ho Chi Minh City")
        String address
) {}
