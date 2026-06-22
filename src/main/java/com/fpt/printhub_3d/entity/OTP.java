package com.fpt.printhub_3d.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "otps")
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
    @Column(nullable = false)
    private String email;
    @Column(name = "otp_code", nullable = false)
    private String otpCode;
    @Column(name = "expiry_time", nullable = false)
    private Instant expiryTime;
}
