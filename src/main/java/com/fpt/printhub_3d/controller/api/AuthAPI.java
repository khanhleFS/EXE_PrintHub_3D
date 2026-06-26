package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.authen.*;
import com.fpt.printhub_3d.dto.maker.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequestMapping("/api/auth")
@Tag(name = "Auth APIs", description = "Authentication and user profile APIs")
public interface AuthAPI {

    @Operation(summary = "Login", description = "Authenticate user and return JWT access token")
    @PostMapping("/login")
    ResponseEntity<ApiResponse<LoginResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO request);

    @Operation(
            summary = "Logout",
            description = "Blacklist current JWT access token",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/logout")
    ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request);

    @Operation(summary = "Register", description = "Register new user account and send OTP")
    @PostMapping("/register")
    ResponseEntity<ApiResponse<RegisterResponseDTO>> register(
            @Valid @RequestBody RegisterRequestDTO request);

    @Operation(summary = "Forgot password", description = "Send new password to user's email")
    @PostMapping("/forgot-password")
    ResponseEntity<ApiResponse<ForgotPasswordResponseDTO>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDTO request);

    @Operation(
            summary = "Reset password",
            description = "Reset password using old password",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/reset-password")
    ResponseEntity<ApiResponse<ResetPasswordResponseDTO>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDTO request);

    @Operation(
            summary = "Get profile",
            description = "Get current authenticated user's profile",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/profile")
    ResponseEntity<ApiResponse<ProfileDTO>> getProfile();

    @Operation(
            summary = "Update profile",
            description = "Update current authenticated user's profile",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping("/profile")
    ResponseEntity<ApiResponse<Void>> updateProfile(
            @Valid @RequestBody ProfileDTO request);

    @Operation(summary = "Verify register OTP", description = "Activate account by register OTP")
    @PostMapping("/verify-register-otp")
    ResponseEntity<ApiResponse<Void>> verifyRegisterOtp(
            @Valid @RequestBody VerifyOTPRequestDTO request);

    @Operation(
            summary = "Apply for Maker registration",
            description = "Submit CCCD image and profile info to apply to be a Maker. Only users with role USER or MAKER are allowed.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/users/maker-registration")
    ResponseEntity<ApiResponse<MakerApplicationResponse>> registerMaker(
            @Valid @RequestBody MakerRegistrationRequest request);

    @Operation(
            summary = "Approve or reject a Maker registration application",
            description = "Admin updates the application status of a Maker to APPROVED or REJECTED. Promotes user's role to MAKER upon approval.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping("/admin/maker-applications/{id}/status")
    ResponseEntity<ApiResponse<MakerApplicationResponse>> updateApplicationStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody MakerStatusUpdateRequest request);

    @Operation(
            summary = "Add a CCCD number to the system blacklist",
            description = "Admin manually bans a CCCD number from being registered or applied in the system.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/admin/blacklist")
    ResponseEntity<ApiResponse<Void>> addCccdToBlacklist(
            @Valid @RequestBody BlacklistRequestDTO request);
}
