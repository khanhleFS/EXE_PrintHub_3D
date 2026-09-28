package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.authen.*;

import java.util.UUID;

public interface AuthService {
    LoginResponseDTO login(LoginRequestDTO request);

    void logout(String authHeader);
    
    void register(RegisterRequestDTO request);

    void sendForgotPasswordOtp(String email);

    ForgotPasswordResponseDTO forgotPassword(ForgotPasswordRequestDTO request);

    void sendResetPasswordOtp(String email);

    ResetPasswordResponseDTO resetPassword(ResetPasswordRequestDTO request);

    ProfileDTO getProfile(UUID id);

    void updateProfile(UUID id, ProfileDTO profile);

    boolean isEmailValid(String email);

    boolean verifyRegisterOtp(String email, String otpCode);
}
