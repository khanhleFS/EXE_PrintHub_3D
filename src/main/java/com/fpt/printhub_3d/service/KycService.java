package com.fpt.printhub_3d.service;

import java.util.Map;

public interface KycService {
    /**
     * Tải ảnh từ URL, quét QR Code CCCD và trích xuất thông tin.
     * Kiểm tra số CCCD trích xuất được xem có nằm trong danh sách đen hay không.
     */
    Map<String, String> extractCccdData(String imageUrl);

    /**
     * Phân tách chuỗi QR Code CCCD của Việt Nam.
     * Cấu trúc thông thường: SốCCCD|SốCMND cũ|HọTên|NgàySinh|GiớiTính|ĐịaChỉ|NgàyCấp
     */
    Map<String, String> parseCccdRawData(String rawData);
}
