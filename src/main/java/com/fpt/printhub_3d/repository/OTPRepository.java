package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.OTP;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OTPRepository extends JpaRepository<OTP, UUID> {
    Optional<OTP> findByEmailAndOtpCode(String email, String otpCode);

    // Xóa toàn bộ OTP cũ của Email này
    void deleteByEmail(String email);

    // Xóa đúng cặp OTP đã dùng
    void deleteByEmailAndOtpCode(String email, String otpCode);
}
