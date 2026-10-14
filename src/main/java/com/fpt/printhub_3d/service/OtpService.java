package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.entity.Enumeration.OtpPurpose;
import com.fpt.printhub_3d.entity.User;

public interface OtpService {
    /**
     * Sinh mã OTP 6 số ngẫu nhiên, mã hóa hash lưu DB, trả về plaintext code chỉ dùng để gửi mail.
     * Kiểm tra cooldown 60s và giới hạn 5 lần/giờ. Vô hiệu hóa challenge cũ của cùng purpose.
     */
    String generateOtpChallenge(User user, OtpPurpose purpose);

    /**
     * Xác thực mã OTP người dùng nhập vào.
     * Tăng attempt_count độc lập (không rollback khi ném lỗi), kiểm tra hạn và số lần thử.
     */
    void verifyOtp(User user, OtpPurpose purpose, String rawOtpCode);
}
