package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.entity.OTP;
import com.fpt.printhub_3d.repository.OTPRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OtpAttemptHelper {

    private final OTPRepository otpRepository;

    /**
     * Tăng số lần thử sai trong transaction riêng (REQUIRES_NEW)
     * Đảm bảo bộ đếm được persist vào database ngay cả khi transaction gọi ném ApiException.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordFailedAttempt(UUID challengeId) {
        OTP challenge = otpRepository.findById(challengeId).orElse(null);
        if (challenge == null) {
            return 1;
        }
        int newAttemptCount = (challenge.getAttemptCount() != null ? challenge.getAttemptCount() : 0) + 1;
        challenge.setAttemptCount(newAttemptCount);
        if (newAttemptCount >= challenge.getMaxAttempts()) {
            challenge.setInvalidatedAt(Instant.now());
            log.warn("Challenge OTP {} đã bị vô hiệu hóa do vượt quá {} lần thử sai.", challengeId, challenge.getMaxAttempts());
        }
        otpRepository.saveAndFlush(challenge);
        return newAttemptCount;
    }

    /**
     * Vô hiệu hóa challenge trong transaction riêng
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void invalidateChallenge(UUID challengeId) {
        OTP challenge = otpRepository.findById(challengeId).orElse(null);
        if (challenge != null) {
            challenge.setInvalidatedAt(Instant.now());
            otpRepository.saveAndFlush(challenge);
        }
    }
}
