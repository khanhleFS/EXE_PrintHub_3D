package com.fpt.printhub_3d.entity;

import com.fpt.printhub_3d.common.infrastructure.BaseEntity;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.Enumeration.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class User extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private UserRole role;

    @Column(name = "username", unique = true, nullable = false, length = 255)
    private String username;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "phone", unique = true, nullable = false)
    private String phone;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "registration_expires_at")
    private Instant registrationExpiresAt;

    public UserStatus getStatus() {
        if (status == null) {
            return Boolean.TRUE.equals(isActive) ? UserStatus.ACTIVE : UserStatus.PENDING_VERIFICATION;
        }
        return status;
    }

    public Boolean getEmailVerified() {
        if (emailVerified == null) {
            return Boolean.TRUE.equals(isActive);
        }
        return emailVerified;
    }

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private PointWallet pointWallet;

    public Integer getRewardPoints() {
        return (pointWallet != null && pointWallet.getBalance() != null) ? pointWallet.getBalance() : 0;
    }

    @Column(name = "cccd_number", unique = true, length = 20)
    private String cccdNumber;

    @Column(name = "cccd_name", length = 255)
    private String cccdName;

    @Column(name = "cccd_dob", length = 20)
    private String cccdDob;

    @Column(name = "cccd_gender", length = 20)
    private String cccdGender;

    @Column(name = "cccd_address", length = 255)
    private String cccdAddress;

    @Column(name = "cccd_front_image_url", length = 500)
    private String cccdFrontImageUrl;

    @Column(length = 50)
    private String studentId;
    @Column(length = 200)
    private String university;

}

