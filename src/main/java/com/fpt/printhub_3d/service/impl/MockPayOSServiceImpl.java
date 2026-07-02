package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkResponseDTO;
import com.fpt.printhub_3d.service.PayOSService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mock implementation cho PayOS.
 * Trả về dữ liệu giả lập để phát triển và test các luồng thanh toán.
 * Khi tích hợp cổng thanh toán thực, tạo class mới implement PayOSService
 * và đánh @Primary hoặc xóa @Service trên class này.
 */
@Slf4j
@Service
public class MockPayOSServiceImpl implements PayOSService {

    private static final String MOCK_BASE_URL = "https://pay.payos.vn/web/";
    private static final String MOCK_QR_BASE_URL = "https://pay.payos.vn/qr/";
    // Link thanh toán mock hết hạn sau 15 phút
    private static final long LINK_EXPIRY_SECONDS = 15 * 60;

    @Override
    public CreatePaymentLinkResponseDTO createPaymentLink(String orderCode, BigDecimal amount, String description) {
        log.info("[MOCK PayOS] Tạo link thanh toán: orderCode={}, amount={}, description={}",
                orderCode, amount, description);

        // Sinh mock payment link và QR URL
        String mockToken = orderCode + "-" + System.currentTimeMillis();
        String paymentLinkUrl = MOCK_BASE_URL + mockToken;
        String qrCodeUrl = MOCK_QR_BASE_URL + mockToken;
        Instant expiredAt = Instant.now().plusSeconds(LINK_EXPIRY_SECONDS);

        log.info("[MOCK PayOS] Link thanh toán đã tạo: {}", paymentLinkUrl);

        return CreatePaymentLinkResponseDTO.builder()
                .paymentLinkUrl(paymentLinkUrl)
                .qrCodeUrl(qrCodeUrl)
                .amount(amount)
                .orderCode(orderCode)
                .expiredAt(expiredAt)
                .build();
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signature) {
        // Mock: luôn trả true để cho phép test luồng webhook
        log.info("[MOCK PayOS] Xác minh chữ ký webhook — mock luôn trả true. signature={}", signature);
        return true;
    }
}
