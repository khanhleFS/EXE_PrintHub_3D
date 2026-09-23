package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.authen.*;
import com.fpt.printhub_3d.dto.maker.BlacklistRequestDTO;

import java.util.UUID;

public interface AuthService {
    LoginResponseDTO login(LoginRequestDTO request);

    // Chuyển logic logout xuống Service
    void logout(String authHeader);
    
    // Đăng ký tài khoản mới và gửi mã OTP
    void register(RegisterRequestDTO request);

    ForgotPasswordResponseDTO forgotPassword(String email);

    ResetPasswordResponseDTO resetPassword(ResetPasswordRequestDTO request);

    ProfileDTO getProfile(UUID id);

    void updateProfile(UUID id, ProfileDTO profile);

    boolean isEmailValid(String email);

    boolean verifyRegisterOtp(String email, String otpCode);

    void addCccdToBlacklist(BlacklistRequestDTO request);
}
