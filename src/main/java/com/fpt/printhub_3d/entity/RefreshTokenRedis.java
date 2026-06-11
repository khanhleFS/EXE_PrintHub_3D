package com.fpt.printhub_3d.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("RefreshToken") // Tên Hash trong Redis
public class RefreshTokenRedis {

    @Id
    private String token; // Khóa chính lưu trên Redis

    private UUID userId;

    private String email;

    @TimeToLive
    private long ttlInSeconds; // Redis tự động xóa bản ghi này sau khoảng thời gian này
}