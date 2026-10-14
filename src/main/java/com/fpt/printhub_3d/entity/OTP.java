package com.fpt.printhub_3d.entity;

import com.fpt.printhub_3d.entity.Enumeration.OtpPurpose;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otps", indexes = {
        @Index(name = "idx_otps_user_purpose", columnList = "user_id, purpose, created_at"),
        @Index(name = "idx_otps_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OTP {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "email")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    @Builder.Default
    private OtpPurpose purpose = OtpPurpose.REGISTER;

    @Column(name = "otp_hash", length = 255)
    private String otpHash;

    @Column(name = "otp_code")
    private String otpCode;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "expiry_time")
    private Instant expiryTime;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 5;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (expiresAt == null && expiryTime != null) {
            expiresAt = expiryTime;
        }
        if (expiryTime == null && expiresAt != null) {
            expiryTime = expiresAt;
        }
        if (otpCode == null && otpHash != null) {
            otpCode = "HASHED";
        }
    }
}
