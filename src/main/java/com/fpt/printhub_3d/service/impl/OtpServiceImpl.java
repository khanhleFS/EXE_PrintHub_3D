package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.entity.Enumeration.OtpPurpose;
import com.fpt.printhub_3d.entity.OTP;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.OTPRepository;
import com.fpt.printhub_3d.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpServiceImpl implements OtpService {

    private final OTPRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpAttemptHelper otpAttemptHelper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public String generateOtpChallenge(User user, OtpPurpose purpose) {
        if (user == null || user.getId() == null) {
            throw new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin người dùng.");
        }

        // 1. Kiểm tra Cooldown 60s đối với cùng User & Purpose
        Optional<OTP> latestOpt = otpRepository.findTopByUser_IdAndPurposeAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
                user.getId(), purpose
        );
        if (latestOpt.isPresent()) {
            OTP latest = latestOpt.get();
            Instant allowResendAfter = latest.getCreatedAt().plusSeconds(60);
            if (Instant.now().isBefore(allowResendAfter)) {
                long remainingSeconds = Duration.between(Instant.now(), allowResendAfter).getSeconds() + 1;
                throw new ApiException(CommonErrorCode.TOO_MANY_REQUESTS,
                        "Vui lòng đợi " + remainingSeconds + " giây trước khi yêu cầu mã OTP mới.");
            }
        }

        // 2. Kiểm tra giới hạn tối đa 5 lần gửi / 1 giờ
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long recentCount = otpRepository.countRecentOtps(user.getId(), purpose, oneHourAgo);
        if (recentCount >= 5) {
            throw new ApiException(CommonErrorCode.TOO_MANY_REQUESTS,
                    "Bạn đã gửi mã OTP quá 5 lần trong 1 giờ qua. Vui lòng thử lại sau.");
        }

        // 3. Vô hiệu hóa challenge cũ của purpose này
        otpRepository.invalidatePreviousChallenges(user.getId(), purpose, Instant.now());

        // 4. Sinh mã OTP 6 chữ số ngẫu nhiên
        String rawOtp = String.format("%06d", secureRandom.nextInt(1_000_000));

        // 5. Hash mã và lưu vào DB với thời hạn 5 phút
        Instant expiresAt = Instant.now().plusSeconds(5 * 60);
        OTP otp = OTP.builder()
                .user(user)
                .email(user.getEmail())
                .purpose(purpose)
                .otpHash(passwordEncoder.encode(rawOtp))
                .expiresAt(expiresAt)
                .expiryTime(expiresAt)
                .attemptCount(0)
                .maxAttempts(5)
                .createdAt(Instant.now())
                .build();
        otpRepository.save(otp);

        log.info("Đã tạo challenge OTP thành công cho user: {}, purpose: {}", user.getEmail(), purpose);
        return rawOtp;
    }

    @Override
    @Transactional
    public void verifyOtp(User user, OtpPurpose purpose, String rawOtpCode) {
        if (user == null || user.getId() == null) {
            throw new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin người dùng.");
        }
        if (rawOtpCode == null || rawOtpCode.trim().length() != 6) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Vui lòng nhập đúng mã OTP gồm 6 chữ số.");
        }

        // 1. Tìm challenge hiện hành
        OTP challenge = otpRepository.findTopByUser_IdAndPurposeAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
                user.getId(), purpose
        ).orElseThrow(() -> new ApiException(CommonErrorCode.INVALID_INPUT, "Không tìm thấy mã OTP hợp lệ hoặc mã đã bị vô hiệu hóa."));

        // 2. Kiểm tra hết hạn
        if (challenge.getExpiresAt() != null && challenge.getExpiresAt().isBefore(Instant.now())) {
            otpAttemptHelper.invalidateChallenge(challenge.getId());
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Mã OTP đã hết hạn. Vui lòng bấm gửi lại mã mới.");
        }

        // 3. Kiểm tra số lần thử
        if (challenge.getAttemptCount() != null && challenge.getAttemptCount() >= challenge.getMaxAttempts()) {
            otpAttemptHelper.invalidateChallenge(challenge.getId());
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Bạn đã nhập sai mã OTP quá 5 lần. Vui lòng yêu cầu mã mới.");
        }

        // 4. Kiểm tra mã OTP khớp hash
        boolean matches = passwordEncoder.matches(rawOtpCode.trim(), challenge.getOtpHash());
        if (!matches) {
            int newAttempt = otpAttemptHelper.recordFailedAttempt(challenge.getId());
            int remaining = challenge.getMaxAttempts() - newAttempt;
            if (remaining <= 0) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Mã OTP không chính xác. Bạn đã hết số lần thử, vui lòng gửi lại mã mới.");
            }
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Mã OTP không chính xác. Bạn còn " + remaining + " lần thử.");
        }

        // 5. Xác thực thành công: đánh dấu consumed
        challenge.setConsumedAt(Instant.now());
        otpRepository.save(challenge);
        log.info("Xác thực OTP thành công cho user: {}, purpose: {}", user.getEmail(), purpose);
    }
}
