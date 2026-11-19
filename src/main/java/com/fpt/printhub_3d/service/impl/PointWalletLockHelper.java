package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.entity.PointWallet;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.PointWalletRepository;
import com.fpt.printhub_3d.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Khóa bản ghi User trước khi khóa/tạo PointWallet nhằm triệt tiêu race condition
 * tạo ví trùng khóa chính khi 2 đơn hoàn tất song song cho người dùng chưa có ví (XU-08).
 */
@Component
@RequiredArgsConstructor
public class PointWalletLockHelper {

    private final UserRepository userRepository;
    private final PointWalletRepository pointWalletRepository;

    public PointWallet lockOrCreateWallet(User buyer, Clock clock) {
        UUID userId = buyer.getId();
        User managedUser = userRepository.findLockedById(userId).orElse(buyer);
        Clock effectiveClock = clock != null ? clock : Clock.systemUTC();
        LocalDateTime now = LocalDateTime.ofInstant(effectiveClock.instant(), ZoneOffset.UTC);

        return pointWalletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> {
                    PointWallet newWallet = PointWallet.builder()
                            .userId(userId)
                            .user(managedUser)
                            .balance(0)
                            .isNew(true)
                            .updatedAt(now)
                            .build();
                    managedUser.setPointWallet(newWallet);
                    return pointWalletRepository.save(newWallet);
                });
    }
}
