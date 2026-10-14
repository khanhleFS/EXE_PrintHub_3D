package com.fpt.printhub_3d.job;

import com.fpt.printhub_3d.repository.OTPRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OtpCleanupJob {

    private final OTPRepository otpRepository;

    /**
     * Chạy định kỳ mỗi giờ
     * Xóa các challenge OTP đã hết hạn hoặc đã sử dụng (consumed/invalidated) quá 24h
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void cleanupOldOtps() {
        try {
            Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
            int deleted = otpRepository.deleteExpiredOtps(cutoff);
            if (deleted > 0) {
                log.info("[OtpCleanupJob] Đã dọn dẹp {} bản ghi OTP cũ (quá 24 giờ).", deleted);
            }
        } catch (Exception e) {
            log.error("[OtpCleanupJob] Lỗi khi dọn dẹp OTP: {}", e.getMessage());
        }
    }
}
