package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.security.CustomUserDetail;
import com.fpt.printhub_3d.common.security.JwtService;
import com.fpt.printhub_3d.common.security.TokenBlacklistService;
import com.fpt.printhub_3d.dto.authen.*;
import com.fpt.printhub_3d.entity.Enumeration.OtpPurpose;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.Enumeration.UserStatus;
import com.fpt.printhub_3d.entity.OTP;
import com.fpt.printhub_3d.entity.RefreshTokenRedis;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.OTPRepository;
import com.fpt.printhub_3d.repository.RefreshTokenRedisRepository;
import com.fpt.printhub_3d.repository.UserRepository;
import com.fpt.printhub_3d.service.AuthService;
import com.fpt.printhub_3d.service.MailService;
import com.fpt.printhub_3d.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final OTPRepository otpRepository;
    private final OtpService otpService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenRedisRepository refreshTokenRepository;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.userNameOrEmail(), request.password())
        );
        CustomUserDetail userDetail = (CustomUserDetail) authentication.getPrincipal();
        User user = userDetail.getUser();
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION || Boolean.FALSE.equals(user.getEmailVerified())) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Tài khoản chưa được kích hoạt hoặc chưa xác thực email. Vui lòng kiểm tra email để hoàn tất đăng ký.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Tài khoản của bạn đã bị tạm khóa. Vui lòng liên hệ quản trị viên.");
        }
        if (user.getStatus() == UserStatus.DISABLED || Boolean.FALSE.equals(user.getIsActive())) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Tài khoản của bạn đã bị vô hiệu hóa.");
        }
        String accessToken = jwtService.generateAccessToken(userDetail);
        String refreshToken = jwtService.generateRefreshToken(userDetail);
        long expirationInSeconds = 7 * 24 * 60 * 60;
        RefreshTokenRedis tokenRedis = RefreshTokenRedis.builder()
                .token(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .ttlInSeconds(expirationInSeconds)
                .build();
        refreshTokenRepository.save(tokenRedis);
        return new LoginResponseDTO(
                accessToken,
                refreshToken,
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }

    @Override
    @Transactional
    public void logout(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            try {
                Date expiration = jwtService.extractExpiration(jwt);
                long remainingTimeMs = expiration.getTime() - System.currentTimeMillis();
                if (remainingTimeMs > 0) {
                    tokenBlacklistService.blacklistToken(jwt, remainingTimeMs);
                }
            } catch (Exception e) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Token không hợp lệ hoặc đã hết hạn");
            }
        }
    }

    @Override
    @Transactional
    public void register(RegisterRequestDTO request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Mật khẩu và xác nhận mật khẩu không khớp.");
        }

        String email = request.email().trim();
        String username = request.username().trim();
        String phone = request.phone().trim();

        // 1. Kiểm tra Email
        Optional<User> existingEmailUser = userRepository.findByEmail(email);
        User user = null;
        if (existingEmailUser.isPresent()) {
            User u = existingEmailUser.get();
            if (u.getStatus() == UserStatus.ACTIVE || Boolean.TRUE.equals(u.getEmailVerified())) {
                throw new ApiException(CommonErrorCode.CONFLICT, "Email đã tồn tại và đã được kích hoạt.");
            }
            if (u.getStatus() == UserStatus.SUSPENDED || u.getStatus() == UserStatus.DISABLED) {
                throw new ApiException(CommonErrorCode.CONFLICT, "Email thuộc tài khoản đã bị khóa hoặc vô hiệu hóa.");
            }
            // PENDING_VERIFICATION
            if (u.getRegistrationExpiresAt() != null && u.getRegistrationExpiresAt().isBefore(Instant.now())) {
                // Đã hết hạn đăng ký 7 ngày -> Xóa bản ghi cũ để đăng ký mới
                otpRepository.deleteByUserId(u.getId());
                userRepository.delete(u);
                userRepository.flush();
            } else {
                // Còn hạn 7 ngày -> Cập nhật thông tin mới nhất và tái sử dụng bản ghi PENDING
                u.setUsername(username);
                u.setFullName(request.fullName().trim());
                u.setPhone(phone);
                u.setPassword(passwordEncoder.encode(request.password()));
                u.setAddress(request.address());
                user = userRepository.save(u);
            }
        }

        if (user == null) {
            // 2. Kiểm tra Username
            Optional<User> existingUsernameUser = userRepository.findByUsername(username);
            if (existingUsernameUser.isPresent()) {
                User unUser = existingUsernameUser.get();
                if (unUser.getStatus() == UserStatus.PENDING_VERIFICATION
                        && unUser.getRegistrationExpiresAt() != null
                        && unUser.getRegistrationExpiresAt().isBefore(Instant.now())) {
                    otpRepository.deleteByUserId(unUser.getId());
                    userRepository.delete(unUser);
                    userRepository.flush();
                } else {
                    throw new ApiException(CommonErrorCode.CONFLICT, "Tên người dùng đã tồn tại");
                }
            }

            // 3. Kiểm tra Phone
            Optional<User> existingPhoneUser = userRepository.findByPhone(phone);
            if (existingPhoneUser.isPresent()) {
                User phUser = existingPhoneUser.get();
                if (phUser.getStatus() == UserStatus.PENDING_VERIFICATION
                        && phUser.getRegistrationExpiresAt() != null
                        && phUser.getRegistrationExpiresAt().isBefore(Instant.now())) {
                    otpRepository.deleteByUserId(phUser.getId());
                    userRepository.delete(phUser);
                    userRepository.flush();
                } else {
                    throw new ApiException(CommonErrorCode.CONFLICT, "Số điện thoại đã tồn tại");
                }
            }

            // Tạo mới User với trạng thái PENDING_VERIFICATION và thời hạn 7 ngày
            user = User.builder()
                    .username(username)
                    .fullName(request.fullName().trim())
                    .email(email)
                    .phone(phone)
                    .password(passwordEncoder.encode(request.password()))
                    .address(request.address())
                    .role(UserRole.USER)
                    .status(UserStatus.PENDING_VERIFICATION)
                    .emailVerified(false)
                    .registrationExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                    .isActive(false)
                    .build();
            user = userRepository.save(user);
        }

        // Tạo OTP challenge qua OtpService
        String rawOtp = otpService.generateOtpChallenge(user, OtpPurpose.REGISTER);

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("fullName", user.getFullName());
            variables.put("otpCode", rawOtp);
            mailService.sendWithTemplate(
                    user.getEmail(),
                    "Xác nhận đăng ký tài khoản PrintHub 3D",
                    "email/otp-email",
                    variables
            );
        } catch (Exception e) {
            log.error("Không thể gửi email xác nhận cho {}: {}", user.getEmail(), e.getMessage());
            // Giữ User PENDING để user có thể bấm gửi lại mã OTP
        }
    }

    @Override
    @Transactional
    public void resendRegisterOtp(String email) {
        if (email == null || email.isBlank()) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Vui lòng cung cấp địa chỉ email.");
        }
        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin đăng ký với email này."));

        if (user.getStatus() != UserStatus.PENDING_VERIFICATION || Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Tài khoản này đã được kích hoạt hoặc không ở trạng thái chờ xác thực.");
        }

        if (user.getRegistrationExpiresAt() != null && user.getRegistrationExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Thời hạn hoàn thành đăng ký (7 ngày) đã hết. Vui lòng đăng ký lại tài khoản.");
        }

        String rawOtp = otpService.generateOtpChallenge(user, OtpPurpose.REGISTER);

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("fullName", user.getFullName());
            variables.put("otpCode", rawOtp);
            mailService.sendWithTemplate(
                    user.getEmail(),
                    "Mã xác nhận đăng ký tài khoản PrintHub 3D (Gửi lại)",
                    "email/otp-email",
                    variables
            );
        } catch (Exception e) {
            log.error("Không thể gửi lại email xác nhận cho {}: {}", user.getEmail(), e.getMessage());
            throw new ApiException(CommonErrorCode.INTERNAL_ERROR, "Không thể gửi email OTP, vui lòng thử lại sau.");
        }
    }

    @Override
    @Transactional
    public void sendForgotPasswordOtp(String email) {
        if (email == null || email.isBlank()) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Vui lòng cung cấp địa chỉ email.");
        }
        User user = userRepository.findByEmail(email.trim()).orElse(null);
        if (user == null) {
            throw new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Email không tồn tại trong hệ thống");
        }
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DISABLED) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Tài khoản của bạn đã bị khóa hoặc vô hiệu hóa.");
        }

        String rawOtp = otpService.generateOtpChallenge(user, OtpPurpose.FORGOT_PASSWORD);

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("fullName", user.getFullName() != null ? user.getFullName() : user.getUsername());
            variables.put("otpCode", rawOtp);

            mailService.sendWithTemplate(
                    email,
                    "Mã xác nhận quên mật khẩu PrintHub 3D",
                    "email/otp-email",
                    variables
            );
        } catch (Exception e) {
            log.error("Lỗi gửi email OTP quên mật khẩu cho {}: {}", email, e.getMessage());
            throw new ApiException(CommonErrorCode.INTERNAL_ERROR, "Lỗi khi gửi email OTP: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ForgotPasswordResponseDTO forgotPassword(ForgotPasswordRequestDTO request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Xác nhận mật khẩu mới không khớp");
        }
        User user = userRepository.findByEmail(request.email().trim()).orElse(null);
        if (user == null) {
            throw new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Người dùng không tồn tại");
        }

        otpService.verifyOtp(user, OtpPurpose.FORGOT_PASSWORD, request.otpCode());

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        return new ForgotPasswordResponseDTO(request.email(), "Mật khẩu đã được đặt lại thành công.", true, null);
    }

    @Override
    @Transactional
    public void sendResetPasswordOtp(String email) {
        if (email == null || email.isBlank()) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Vui lòng cung cấp địa chỉ email.");
        }
        User user = userRepository.findByEmail(email.trim()).orElse(null);
        if (user == null) {
            throw new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Người dùng không tồn tại");
        }
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DISABLED) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Tài khoản của bạn đã bị khóa hoặc vô hiệu hóa.");
        }

        String rawOtp = otpService.generateOtpChallenge(user, OtpPurpose.CHANGE_PASSWORD);

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("fullName", user.getFullName() != null ? user.getFullName() : user.getUsername());
            variables.put("otpCode", rawOtp);

            mailService.sendWithTemplate(
                    email,
                    "Mã xác nhận đổi mật khẩu PrintHub 3D",
                    "email/otp-email",
                    variables
            );
        } catch (Exception e) {
            log.error("Lỗi gửi email OTP đổi mật khẩu cho {}: {}", email, e.getMessage());
            throw new ApiException(CommonErrorCode.INTERNAL_ERROR, "Lỗi khi gửi email OTP: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ResetPasswordResponseDTO resetPassword(ResetPasswordRequestDTO request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Xác nhận mật khẩu mới không khớp");
        }

        User user = userRepository.findByEmail(request.email().trim()).orElse(null);
        if (user == null) {
            throw new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Người dùng không tồn tại");
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Mật khẩu cũ không chính xác");
        }

        otpService.verifyOtp(user, OtpPurpose.CHANGE_PASSWORD, request.otpCode());

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        return new ResetPasswordResponseDTO(request.email(), "Mật khẩu đã được cập nhật thành công.", user.getId(), null);
    }

    @Override
    public ProfileDTO getProfile(UUID id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Người dùng không tồn tại");
        }
        return ProfileDTO.builder()
                .id(user.getId()).role(user.getRole())
                .studentId(user.getStudentId()).university(user.getUniversity())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .address(user.getAddress())
                .rewardPoints(user.getRewardPoints())
                .build();
    }

    @Override
    @Transactional
    public void updateProfile(UUID id, ProfileDTO profile) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Người dùng không tồn tại");
        }
        userRepository.findByEmail(profile.email()).ifPresent(existingUser -> {
            if (!existingUser.getId().equals(id)) {
                throw new ApiException(CommonErrorCode.CONFLICT, "Email đã tồn tại");
            }
        });
        userRepository.findByPhone(profile.phone()).ifPresent(existingUser -> {
            if (!existingUser.getId().equals(id)) {
                throw new ApiException(CommonErrorCode.CONFLICT, "Số điện thoại đã tồn tại");
            }
        });

        user.setFullName(profile.fullName());
        user.setEmail(profile.email());
        user.setPhone(profile.phone());
        user.setAddress(profile.address());
        user.setStudentId(profile.studentId());
        user.setUniversity(profile.university());
        userRepository.save(user);
    }

    @Override
    public boolean isEmailValid(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    @Override
    @Transactional
    public boolean verifyRegisterOtp(String email, String otpCode) {
        if (email == null || email.isBlank() || otpCode == null || otpCode.isBlank()) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Vui lòng nhập đầy đủ email và mã OTP.");
        }
        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng"));

        if (user.getStatus() == UserStatus.ACTIVE && Boolean.TRUE.equals(user.getEmailVerified())) {
            return true;
        }

        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Tài khoản không ở trạng thái chờ xác thực.");
        }

        if (user.getRegistrationExpiresAt() != null && user.getRegistrationExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Thời hạn xác thực đăng ký (7 ngày) đã hết. Vui lòng đăng ký lại tài khoản mới.");
        }

        // Xác thực qua OtpService
        otpService.verifyOtp(user, OtpPurpose.REGISTER, otpCode.trim());

        // Kích hoạt User
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        user.setIsActive(true);
        userRepository.save(user);

        return true;
    }

    public String generateRandomPassword() {
        int length = 10;
        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < length; i++) {
            int randomIndex = random.nextInt(charSet.length());
            password.append(charSet.charAt(randomIndex));
        }
        return password.toString();
    }
}
