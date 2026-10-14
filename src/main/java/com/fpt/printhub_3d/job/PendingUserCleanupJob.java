package com.fpt.printhub_3d.job;

import com.fpt.printhub_3d.entity.Enumeration.UserStatus;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CartRepository;
import com.fpt.printhub_3d.repository.OTPRepository;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.PointWalletRepository;
import com.fpt.printhub_3d.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingUserCleanupJob {

    private final UserRepository userRepository;
    private final OTPRepository otpRepository;
    private final CartRepository cartRepository;
    private final PointWalletRepository pointWalletRepository;
    private final OrderRepository orderRepository;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private static final String LOCK_KEY = "lock:cleanup:pending_users";

    /**
     * Chạy định kỳ lúc 02:00 sáng hàng ngày (GMT+7)
     * Quét các tài khoản PENDING_VERIFICATION quá 7 ngày chưa xác thực để dọn dẹp an toàn.
     */
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Ho_Chi_Minh")
    public void cleanupExpiredPendingUsers() {
        String lockOwner = UUID.randomUUID().toString();
        boolean lockAcquired = false;

        if (redisTemplate != null) {
            try {
                Boolean success = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockOwner, Duration.ofMinutes(15));
                lockAcquired = Boolean.TRUE.equals(success);
                if (!lockAcquired) {
                    log.info("[PendingUserCleanupJob] Bỏ qua lượt chạy do một node khác đang giữ khóa.");
                    return;
                }
            } catch (Exception e) {
                log.warn("[PendingUserCleanupJob] Lỗi khi kết nối Redis lock, tiếp tục chạy độc lập: {}", e.getMessage());
                lockAcquired = true;
            }
        } else {
            lockAcquired = true;
        }

        int examined = 0;
        int deleted = 0;
        int skipped = 0;
        int failed = 0;

        try {
            log.info("[PendingUserCleanupJob] Bắt đầu quét các tài khoản PENDING hết hạn (quá 7 ngày)...");
            List<User> expiredUsers = userRepository.findTop200ByStatusAndEmailVerifiedFalseAndRegistrationExpiresAtLessThanEqualOrderByRegistrationExpiresAtAsc(
                    UserStatus.PENDING_VERIFICATION, Instant.now()
            );
            examined = expiredUsers.size();

            for (User user : expiredUsers) {
                try {
                    boolean hasOrders = orderRepository.existsByBuyerId(user.getId());
                    if (hasOrders) {
                        log.warn("[PendingUserCleanupJob] Bỏ qua user {} vì có quan hệ đơn hàng bất thường.", user.getId());
                        skipped++;
                        continue;
                    }

                    deletePendingUserData(user);
                    deleted++;
                } catch (Exception ex) {
                    log.error("[PendingUserCleanupJob] Lỗi khi xóa user PENDING {}: {}", user.getId(), ex.getMessage());
                    failed++;
                }
            }

            log.info("[PendingUserCleanupJob] Hoàn thành: quét={}, đã xóa={}, bỏ qua={}, lỗi={}",
                    examined, deleted, skipped, failed);
        } finally {
            if (redisTemplate != null && lockAcquired) {
                try {
                    String currentOwner = redisTemplate.opsForValue().get(LOCK_KEY);
                    if (lockOwner.equals(currentOwner)) {
                        redisTemplate.delete(LOCK_KEY);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Transactional
    public void deletePendingUserData(User user) {
        cartRepository.deleteByUserId(user.getId());
        pointWalletRepository.deleteByUserId(user.getId());
        otpRepository.deleteByUserId(user.getId());
        userRepository.delete(user);
    }
}
