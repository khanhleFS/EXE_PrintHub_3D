package com.fpt.printhub_3d.dto.authen;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @NotBlank(message = "Họ tên không được để trống.")
        @Size(min = 9, max = 50, message = "Họ tên phải từ 9 đến 50 ký tự.")
        @Pattern(regexp = "^\\S.*\\S$|^\\S$", message = "Họ tên không được có khoảng trắng ở đầu hoặc cuối.")
        @Pattern(regexp = "^[\\p{L} ]+$", message = "Họ tên chỉ được chứa chữ cái và khoảng trắng.")
        @Pattern(regexp = "^(?!.*\\s{2,}).*$", message = "Họ tên không được có 2 khoảng trắng liên tiếp.")
        @Schema(example = "Nguyen Van A")
        String fullName,

        @NotBlank(message = "Email không được để trống.")
        @Email(message = "Email không đúng định dạng.")
        @Size(message = "Email phải có độ dài từ 10 đến 50 ký tự.", min = 10, max = 50)
        @Schema(example = "user@example.com")
        String email,

        @NotBlank(message = "Tên người dùng không được để trống.")
        @Size(min = 5, max = 50, message = "Tên người dùng phải từ 5 đến 50 ký tự.")
        @Pattern(regexp = "^\\S.*\\S$|^\\S$", message = "Tên người dùng không được có khoảng trắng ở đầu hoặc cuối.")
        @Pattern(regexp = "^[\\p{L}0-9_]+$", message = "Tên người dùng chỉ được chứa chữ cái, số và dấu gạch dưới.")
        @Pattern(regexp = "^(?!.*__).*$", message = "Tên người dùng không được có 2 dấu gạch dưới liên tiếp.")
        @Schema(example = "nguyenvana")
        String username,

        @NotBlank(message = "Số điện thoại không được để trống.")
        @Pattern(regexp = "^0[3|5|7|8|9][0-9]{8}$",
                message = "Số điện thoại không đúng định dạng (VD: 0912345678).")
        @Schema(example = "0912345678")
        String phone,

        @NotBlank(message = "Mật khẩu không được để trống.")
        @Size(min = 8, max = 50, message = "Mật khẩu phải từ 8 đến 50 ký tự.")
        @Pattern(regexp = ".*[A-Z].*", message = "Mật khẩu phải có ít nhất 1 chữ hoa.")
        @Pattern(regexp = ".*[a-z].*", message = "Mật khẩu phải có ít nhất 1 chữ thường.")
        @Pattern(regexp = ".*[0-9].*", message = "Mật khẩu phải có ít nhất 1 chữ số.")
        @Pattern(regexp = ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*", message = "Mật khẩu phải có ít nhất 1 ký tự đặc biệt.")
        @Pattern(regexp = "^\\S+$", message = "Mật khẩu không được chứa khoảng trắng.")
        @Schema(example = "P@ssw0rd123")
        String password,

        @NotBlank(message = "Xác nhận mật khẩu không được để trống.")
        @Schema(example = "P@ssw0rd123")
        String confirmPassword,

        @NotBlank(message = "Địa chỉ không được để trống.")
        @Size(max = 200, message = "Địa chỉ không được vượt quá 200 ký tự.")
        @Pattern(regexp = "^\\S.*\\S$|^\\S$", message = "Địa chỉ không được có khoảng trắng ở đầu hoặc cuối.")
        @Pattern(regexp = "^(?!.*\\s{2,}).*$", message = "Địa chỉ không được có 2 khoảng trắng liên tiếp.")
        @Schema(example = "123 Nguyen Trai, District 1, Ho Chi Minh City")
        String address,

        @Schema(example = "false")
        Boolean isActive
        ){
}
