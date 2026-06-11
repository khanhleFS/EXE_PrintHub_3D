package com.fpt.printhub_3d.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {
    private String secret;
    private long accessTokenExpiration; // Đơn vị: mili-giây (ví dụ: 900000 cho 15 phút)
    private long refreshTokenExpiration;// Đơn vị: mili-giây (ví dụ: 604800000 cho 7 ngày)
}
