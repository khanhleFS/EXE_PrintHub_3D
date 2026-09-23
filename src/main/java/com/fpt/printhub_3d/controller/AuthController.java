package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.AuthAPI;
import com.fpt.printhub_3d.dto.authen.*;
import com.fpt.printhub_3d.dto.maker.BlacklistRequestDTO;
import com.fpt.printhub_3d.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AuthController implements AuthAPI {
    private final AuthService authService;

    @Override
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(LoginRequestDTO loginRequestDTO) {
        LoginResponseDTO loginResponse = authService.login(loginRequestDTO);
        return ResponseEntity.ok(ApiResponse.<LoginResponseDTO>builder()
                .code(200)
                .message("Đăng nhập thành công")
                .result(loginResponse)
                .build());
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        authService.logout(authHeader);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Đăng xuất thành công")
                .build());
    }

    @Override
    public ResponseEntity<ApiResponse<RegisterResponseDTO>> register(RegisterRequestDTO registerRequestDTO) {
        authService.register(registerRequestDTO);
        return ResponseEntity.ok(ApiResponse.<RegisterResponseDTO>builder()
                .code(200)
                .message("Đăng ký thành công. Vui lòng kiểm tra email để kích hoạt tài khoản.")
                .build());
    }

    @Override
    public ResponseEntity<ApiResponse<ForgotPasswordResponseDTO>> forgotPassword(ForgotPasswordRequestDTO requestDTO) {
        ForgotPasswordResponseDTO result = authService.forgotPassword(requestDTO.email());
        return ResponseEntity.ok(ApiResponse.<ForgotPasswordResponseDTO>builder()
                .code(200)
                .message("Yêu cầu quên mật khẩu thành công")
                .result(result)
                .build());
    }

    @Override
    public ResponseEntity<ApiResponse<ResetPasswordResponseDTO>> resetPassword(ResetPasswordRequestDTO requestDTO) {
        ResetPasswordResponseDTO result = authService.resetPassword(requestDTO);
        return ResponseEntity.ok(ApiResponse.<ResetPasswordResponseDTO>builder()
                .code(200)
                .message("Đặt lại mật khẩu thành công")
                .result(result)
                .build());
    }

    @Override
    public ResponseEntity<ApiResponse<ProfileDTO>> getProfile() {
        // Lấy thông tin user hiện tại từ SecurityContext
        ProfileDTO profile = authService.getProfile(SecurityUtils.getCurrentUser().getId());
        return ResponseEntity.ok(ApiResponse.<ProfileDTO>builder()
                .code(200)
                .message("Lấy thông tin cá nhân thành công")
                .result(profile)
                .build());
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> updateProfile(ProfileDTO profileDTO) {
        authService.updateProfile(SecurityUtils.getCurrentUser().getId(), profileDTO);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Cập nhật thông tin cá nhân thành công")
                .build());
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> verifyRegisterOtp(VerifyOTPRequestDTO requestDTO) {
        authService.verifyRegisterOtp(requestDTO.email(), requestDTO.otpCode());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Xác thực thành công. Tài khoản đã được kích hoạt.")
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> addCccdToBlacklist(BlacklistRequestDTO request) {
        authService.addCccdToBlacklist(request);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(200)
                        .message("Đã thêm số CCCD vào danh sách đen và vô hiệu hóa tài khoản liên quan.")
                        .build()
        );
    }
}
