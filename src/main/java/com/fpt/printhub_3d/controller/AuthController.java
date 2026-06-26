package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.controller.api.AuthAPI;
import com.fpt.printhub_3d.dto.authen.*;
import com.fpt.printhub_3d.dto.maker.BlacklistRequestDTO;
import com.fpt.printhub_3d.dto.maker.MakerApplicationResponse;
import com.fpt.printhub_3d.dto.maker.MakerRegistrationRequest;
import com.fpt.printhub_3d.dto.maker.MakerStatusUpdateRequest;
import com.fpt.printhub_3d.service.AuthService;
import com.fpt.printhub_3d.common.security.CustomUserDetail;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
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
        CustomUserDetail userDetail = (CustomUserDetail) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ProfileDTO profile = authService.getProfile(userDetail.getUser().getId());
        return ResponseEntity.ok(ApiResponse.<ProfileDTO>builder()
                .code(200)
                .message("Lấy thông tin cá nhân thành công")
                .result(profile)
                .build());
    }
    @Override
    public ResponseEntity<ApiResponse<Void>> updateProfile(ProfileDTO profileDTO) {
        CustomUserDetail userDetail = (CustomUserDetail) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        authService.updateProfile(userDetail.getUser().getId(), profileDTO);
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
    @PreAuthorize("hasAnyRole('USER', 'MAKER')")
    public ResponseEntity<ApiResponse<MakerApplicationResponse>> registerMaker(MakerRegistrationRequest request) {
        CustomUserDetail userDetail = (CustomUserDetail) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        MakerApplicationResponse response = authService.registerMaker(userDetail.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<MakerApplicationResponse>builder()
                        .code(201)
                        .message("Gửi yêu cầu đăng ký Maker thành công. Chờ Admin phê duyệt.")
                        .result(response)
                        .build()
        );
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MakerApplicationResponse>> updateApplicationStatus(UUID id, MakerStatusUpdateRequest request) {
        MakerApplicationResponse response = authService.updateMakerApplicationStatus(id, request);
        return ResponseEntity.ok(
                ApiResponse.<MakerApplicationResponse>builder()
                        .code(200)
                        .message("Cập nhật trạng thái hồ sơ Maker thành công.")
                        .result(response)
                        .build()
        );
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

