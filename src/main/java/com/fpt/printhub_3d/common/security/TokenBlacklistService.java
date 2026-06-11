package com.fpt.printhub_3d.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {
    private final StringRedisTemplate redisTemplate;
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    /**
     * Lưu Access Token vào blacklist của Redis
     * @param token Chuỗi JWT cần đưa vào danh sách đen
     * @param expirationTimeMs Thời gian sống (TTL) còn lại của token tính bằng mili-giây
     */
    public void blacklistToken(String token, long expirationTimeMs) {
        if (expirationTimeMs > 0) {
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + token,
                    "blacklisted",
                    expirationTimeMs,
                    TimeUnit.MILLISECONDS
            );
        }
    }
    /**
     * Kiểm tra token có nằm trong blacklist không
     */
    public boolean isTokenBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }
}

