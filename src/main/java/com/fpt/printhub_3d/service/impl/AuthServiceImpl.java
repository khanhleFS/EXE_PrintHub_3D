package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.security.CustomUserDetail;
import com.fpt.printhub_3d.common.security.JwtService;
import com.fpt.printhub_3d.common.security.TokenBlacklistService;
import com.fpt.printhub_3d.dto.authen.*;
import com.fpt.printhub_3d.dto.maker.BlacklistRequestDTO;
import com.fpt.printhub_3d.dto.maker.MakerApplicationResponse;
import com.fpt.printhub_3d.dto.maker.MakerRegistrationRequest;
import com.fpt.printhub_3d.dto.maker.MakerStatusUpdateRequest;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.MakerProfile;
import com.fpt.printhub_3d.entity.OTP;
import com.fpt.printhub_3d.entity.RefreshTokenRedis;
import com.fpt.printhub_3d.entity.SystemBlacklist;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.MakerProfileRepository;
import com.fpt.printhub_3d.repository.OTPRepository;
import com.fpt.printhub_3d.repository.RefreshTokenRedisRepository;
import com.fpt.printhub_3d.repository.SystemBlacklistRepository;
import com.fpt.printhub_3d.repository.UserRepository;
import com.fpt.printhub_3d.service.AuthService;
import com.fpt.printhub_3d.service.KycService;
import com.fpt.printhub_3d.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final MailService mailService;

    private final OTPRepository otpRepository;

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenRedisRepository refreshTokenRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final KycService kycService;
    private final MakerProfileRepository makerProfileRepository;
    private final SystemBlacklistRepository systemBlacklistRepository;

    @Override
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request) {
        // Thực hiện authenticate thông qua authenticationManager
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.userNameOrEmail(), request.password())
        );
        CustomUserDetail userDetail = (CustomUserDetail) authentication.getPrincipal();
        User user = userDetail.getUser();
        // Kiểm tra xem tài khoản đã được kích hoạt hay chưa
        if (!user.getIsActive()) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Tài khoản chưa được kích hoạt. Vui lòng kiểm tra email để xác nhận.");
        }
        // Sinh JWT
        String accessToken = jwtService.generateAccessToken(userDetail);
        String refreshToken = jwtService.generateRefreshToken(userDetail);
        // Lưu Refresh Token vào Redis
        long expirationInSeconds = 7 * 24 * 60 * 60; // 7 ngày
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
                // Token đã hết hạn, không cần đưa vào blacklist nữa
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Token không hợp lệ hoặc đã hết hạn");
            }
        }
    }

    @Override
    @Transactional
    public void register(RegisterRequestDTO request) {
        // Validate password trùng khớp
        if (!request.password().equals(request.confirmPassword())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Mật khẩu và xác nhận mật khẩu không khớp.");
        }
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ApiException(CommonErrorCode.CONFLICT, "Email đã tồn tại");
        }
        if (userRepository.findByPhone(request.phone()).isPresent()) {
            throw new ApiException(CommonErrorCode.CONFLICT, "Số điện thoại đã tồn tại");
        }
        // Xử lý thông tin CCCD tùy chọn
        String cccdNumber = null;
        String cccdName = null;
        String cccdDob = null;
        String cccdGender = null;
        String cccdAddress = null;
        String cccdFrontImageUrl = null;

        if (request.cccdFrontImageUrl() != null && !request.cccdFrontImageUrl().isBlank()) {
            Map<String, String> extracted = kycService.extractCccdData(request.cccdFrontImageUrl());
            cccdNumber = extracted.get("cccdNumber");
            cccdName = extracted.get("fullName");
            cccdDob = extracted.get("dob");
            cccdGender = extracted.get("gender");
            cccdAddress = extracted.get("address");
            cccdFrontImageUrl = request.cccdFrontImageUrl();

            // Kiểm tra số CCCD đã tồn tại trong hệ thống chưa
            if (cccdNumber != null && userRepository.existsByCccdNumber(cccdNumber)) {
                throw new ApiException(CommonErrorCode.CONFLICT, "Số CCCD này đã được sử dụng để đăng ký tài khoản khác.");
            }
        }

        // Cưỡng chế trạng thái ban đầu là FALSE để yêu cầu verify OTP
        User user = User.builder()
                .username(request.username())
                .fullName(request.fullName())
                .email(request.email())
                .phone(request.phone())
                .password(passwordEncoder.encode(request.password()))
                .address(request.address())
                .role(UserRole.USER)
                .isActive(false) // Mặc định là false
                .cccdNumber(cccdNumber)
                .cccdName(cccdName)
                .cccdDob(cccdDob)
                .cccdGender(cccdGender)
                .cccdAddress(cccdAddress)
                .cccdFrontImageUrl(cccdFrontImageUrl)
                .build();
        userRepository.save(user);
        // Sinh OTP 6 số
        String otpCode = String.format("%06d", new SecureRandom().nextInt(999999));
        // Xóa các OTP cũ của email này trước
        otpRepository.deleteByEmail(request.email());
        // Lưu OTP mới
        OTP otp = OTP.builder()
                .email(request.email())
                .otpCode(otpCode)
                .expiryTime(Instant.now().plusSeconds(5 * 60))
                .build();
        otpRepository.save(otp);
        // Gửi mail
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("fullName", request.fullName());
            variables.put("otpCode", otpCode);
            mailService.sendWithTemplate(
                    request.email(),
                    "Xác nhận đăng ký tài khoản",
                    "email/otp-email",
                    variables
            );
        } catch (Exception e) {
            throw new ApiException(CommonErrorCode.INTERNAL_ERROR, "Lỗi gửi email: " + e.getMessage());
        }
    }

    @Override
    public ForgotPasswordResponseDTO forgotPassword(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Email không tồn tại");
        }
        String newPassword = generateRandomPassword();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("fullName", user.getUsername());
            variables.put("newPassword", newPassword);

            mailService.sendWithTemplate(
                    email,
                    "Quên mật khẩu",
                    "email/forgot-password-email",
                    variables
            );
        } catch (Exception e) {
            throw new ApiException(CommonErrorCode.INTERNAL_ERROR, "Lỗi gửi email: " + e.getMessage());
        }
        return new ForgotPasswordResponseDTO(email, "Mật khẩu mới đã được gửi đến email của bạn.", true, null);
    }

    @Override
    public ResetPasswordResponseDTO resetPassword(ResetPasswordRequestDTO request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Người dùng không tồn tại");
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Mật khẩu cũ không khớp");
        }
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
                .fullName(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .address(user.getAddress())
                .build();
    }

    @Override
    public void updateProfile(UUID id, ProfileDTO profile) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Người dùng không tồn tại");
        }
        // Check if email or phone is already taken by another user
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
        // Update user profile
        user.setUsername(profile.fullName());
        user.setEmail(profile.email());
        user.setPhone(profile.phone());
        user.setAddress(profile.address());
        userRepository.save(user);
    }

    @Override
    public boolean isEmailValid(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    @Override
    @Transactional
    public boolean verifyRegisterOtp(String email, String otpCode) {
        OTP otp = otpRepository.findByEmailAndOtpCode(email, otpCode)
                .orElseThrow(() -> new ApiException(CommonErrorCode.INVALID_INPUT, "Mã OTP không hợp lệ"));
        if (otp.getExpiryTime().isBefore(Instant.now())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Mã OTP đã hết hạn");
        }
        // Kích hoạt User
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng"));
        user.setIsActive(true);
        userRepository.save(user);
        // Xóa OTP sau khi dùng thành công
        otpRepository.deleteByEmail(email);
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

    @Override
    @Transactional
    public MakerApplicationResponse registerMaker(UUID userId, MakerRegistrationRequest request) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng hiện tại"));

        Map<String, String> extracted = kycService.extractCccdData(request.cccdFrontImageUrl());
        String cccdNumber = extracted.get("cccdNumber");

        userRepository.findByCccdNumber(cccdNumber).ifPresent(existingUser -> {
            if (!existingUser.getId().equals(currentUser.getId())) {
                throw new ApiException(CommonErrorCode.CONFLICT, "Số CCCD này đã được đăng ký cho một tài khoản khác.");
            }
        });

        currentUser.setCccdNumber(cccdNumber);
        currentUser.setCccdName(extracted.get("fullName"));
        currentUser.setCccdDob(extracted.get("dob"));
        currentUser.setCccdGender(extracted.get("gender"));
        currentUser.setCccdAddress(extracted.get("address"));
        currentUser.setCccdFrontImageUrl(request.cccdFrontImageUrl());
        userRepository.save(currentUser);

        MakerProfile makerProfile = makerProfileRepository.findById(currentUser.getId()).orElse(null);
        if (makerProfile == null) {
            makerProfile = new MakerProfile();
            makerProfile.setId(currentUser.getId());
            makerProfile.setUsers(currentUser);
            makerProfile.setTrustScore(0.0);
        }
        makerProfile.setBusinessName(request.businessName());
        makerProfile.setEquipmentInfo(request.equipmentInfo());
        makerProfile.setPortfolioUrl(request.portfolioUrl());
        makerProfile.setBio(request.bio());
        makerProfile.setStatus("PENDING");
        makerProfileRepository.save(makerProfile);

        return MakerApplicationResponse.builder()
                .applicationId(currentUser.getId())
                .extractedName(currentUser.getCccdName())
                .extractedIdNumber(currentUser.getCccdNumber())
                .status(makerProfile.getStatus())
                .build();
    }

    @Override
    @Transactional
    public MakerApplicationResponse updateMakerApplicationStatus(UUID id, MakerStatusUpdateRequest request) {
        MakerProfile makerProfile = makerProfileRepository.findById(id)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ đăng ký Maker của người dùng này."));

        User user = makerProfile.getUsers();

        if ("APPROVED".equalsIgnoreCase(request.status())) {
            makerProfile.setStatus("APPROVED");
            makerProfile.setVerifiedAt(Instant.now());
            user.setRole(UserRole.MAKER);
            userRepository.save(user);
        } else if ("REJECTED".equalsIgnoreCase(request.status())) {
            makerProfile.setStatus("REJECTED");
        }

        makerProfileRepository.save(makerProfile);

        return MakerApplicationResponse.builder()
                .applicationId(makerProfile.getId())
                .extractedName(user.getCccdName())
                .extractedIdNumber(user.getCccdNumber())
                .status(makerProfile.getStatus())
                .build();
    }

    @Override
    @Transactional
    public void addCccdToBlacklist(BlacklistRequestDTO request) {
        if (systemBlacklistRepository.existsByCccdNumber(request.cccdNumber())) {
            throw new ApiException(CommonErrorCode.CONFLICT, "Số CCCD này đã tồn tại trong danh sách đen.");
        }

        SystemBlacklist entry = SystemBlacklist.builder()
                .cccdNumber(request.cccdNumber())
                .reason(request.reason())
                .blacklistedAt(Instant.now())
                .build();
        systemBlacklistRepository.save(entry);

        userRepository.findByCccdNumber(request.cccdNumber()).ifPresent(user -> {
            user.setIsActive(false);
            userRepository.save(user);

            makerProfileRepository.findById(user.getId()).ifPresent(profile -> {
                profile.setStatus("SUSPENDED");
                makerProfileRepository.save(profile);
            });
        });
    }
}
