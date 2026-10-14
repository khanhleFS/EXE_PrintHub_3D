package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Enumeration.OtpPurpose;
import com.fpt.printhub_3d.entity.OTP;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OTPRepository extends JpaRepository<OTP, UUID> {

    // Tìm challenge hiện hành mới nhất cho User và Purpose
    Optional<OTP> findTopByUser_IdAndPurposeAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
            UUID userId, OtpPurpose purpose
    );

    // Đếm số lần gửi OTP trong một khoảng thời gian (dùng cho rate limit 5 lần/giờ)
    @Query("SELECT COUNT(o) FROM OTP o WHERE o.user.id = :userId AND o.purpose = :purpose AND o.createdAt >= :since")
    long countRecentOtps(@Param("userId") UUID userId, @Param("purpose") OtpPurpose purpose, @Param("since") Instant since);

    // Vô hiệu hóa các challenge cũ chưa dùng
    @Modifying
    @Query("UPDATE OTP o SET o.invalidatedAt = :now WHERE o.user.id = :userId AND o.purpose = :purpose AND o.consumedAt IS NULL AND o.invalidatedAt IS NULL")
    void invalidatePreviousChallenges(@Param("userId") UUID userId, @Param("purpose") OtpPurpose purpose, @Param("now") Instant now);

    // Xóa OTP hết hạn hoặc đã dùng sau 24h (dùng cho cleanup scheduler)
    @Modifying
    @Query("DELETE FROM OTP o WHERE (o.expiresAt IS NOT NULL AND o.expiresAt < :cutoff) OR (o.consumedAt IS NOT NULL AND o.consumedAt < :cutoff) OR (o.invalidatedAt IS NOT NULL AND o.invalidatedAt < :cutoff)")
    int deleteExpiredOtps(@Param("cutoff") Instant cutoff);

    // Xóa toàn bộ OTP của 1 User (khi xóa User PENDING)
    @Modifying
    @Query("DELETE FROM OTP o WHERE o.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    // Phương thức cũ để tương thích ngược
    Optional<OTP> findByEmailAndOtpCode(String email, String otpCode);

    void deleteByEmail(String email);

    void deleteByEmailAndOtpCode(String email, String otpCode);
}
