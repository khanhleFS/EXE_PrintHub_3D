package com.fpt.printhub_3d.entity;

import com.fpt.printhub_3d.common.infrastructure.BaseEntity;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

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

}

