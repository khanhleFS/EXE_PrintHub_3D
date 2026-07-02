package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkResponseDTO;

import java.math.BigDecimal;

/**
 * Interface tích hợp cổng thanh toán PayOS.
 * Hiện tại sử dụng MockPayOSServiceImpl.
 * Khi chọn được cổng thanh toán thực, tạo implementation mới thay thế mock.
 */
public interface PayOSService {

    /**
     * Tạo link thanh toán và mã QR động trên PayOS.
     *
     * @param orderCode   mã đơn hàng nội bộ
     * @param amount      số tiền cần thanh toán (VND)
     * @param description mô tả giao dịch
     * @return thông tin link thanh toán và QR code
     */
    CreatePaymentLinkResponseDTO createPaymentLink(String orderCode, BigDecimal amount, String description);

    /**
     * Xác minh chữ ký webhook IPN từ PayOS.
     *
     * @param rawPayload  nội dung payload gốc
     * @param signature   chữ ký từ PayOS
     * @return true nếu chữ ký hợp lệ
     */
    boolean verifyWebhookSignature(String rawPayload, String signature);
}
