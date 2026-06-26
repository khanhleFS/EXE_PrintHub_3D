package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.authen.*;
import com.fpt.printhub_3d.dto.maker.BlacklistRequestDTO;
import com.fpt.printhub_3d.dto.maker.MakerApplicationResponse;
import com.fpt.printhub_3d.dto.maker.MakerRegistrationRequest;
import com.fpt.printhub_3d.dto.maker.MakerStatusUpdateRequest;

import java.util.UUID;

public interface AuthService {
    LoginResponseDTO login(LoginRequestDTO request);

    // Chuyển logic logout xuống Service
    void logout(String authHeader);
    // Sửa thành void, ném Exception trực tiếp nếu lỗi (Theo chuẩn Error Handling)
    void register(RegisterRequestDTO request);

    ForgotPasswordResponseDTO forgotPassword(String email);

    ResetPasswordResponseDTO resetPassword(ResetPasswordRequestDTO request);

    ProfileDTO getProfile(UUID id);

    void updateProfile(UUID id, ProfileDTO profile);

    boolean isEmailValid(String email);

    boolean verifyRegisterOtp(String email, String otpCode);

    MakerApplicationResponse registerMaker(UUID userId, MakerRegistrationRequest request);

    MakerApplicationResponse updateMakerApplicationStatus(UUID id, MakerStatusUpdateRequest request);

    void addCccdToBlacklist(BlacklistRequestDTO request);
}
