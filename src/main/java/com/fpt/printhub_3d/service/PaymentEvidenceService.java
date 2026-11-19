package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.entity.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Hợp đồng chia sẻ giữa BE 1 và BE 2 về chứng từ thu tiền (PayOS SUCCESS / COD đã thu / Đơn 0đ ưu đãi toàn bộ).
 * BE 2 dùng để xét thưởng xu (Luồng 03) và ghi nhận chứng từ COD idempotent khi admin xác nhận thu tiền.
 * Khi BE 1 bàn giao bản PaymentEvidenceServiceImpl chính thức ở Đợt 2, Spring sẽ tự động dùng bean của BE 1.
 */
public interface PaymentEvidenceService {
    String ZERO_AMOUNT_PROMO = "ZERO_AMOUNT_PROMO";

    enum SourceType {
        CHECKOUT_ORDER,
        ORDER,
        CUSTOM_ORDER
    }

    record PaymentEvidence(
            UUID paymentId,
            SourceType sourceType,
            UUID sourceId,
            String gateway,
            BigDecimal paidAmount,
            Instant paidAt,
            boolean zeroAmountPromo
    ) {}

    /**
     * Tra cứu chứng từ thanh toán hợp lệ (SUCCESS hoặc căn cứ miễn phí 0đ ưu đãi toàn bộ) của nguồn gốc.
     */
    Optional<PaymentEvidence> findSettledEvidence(SourceType sourceType, UUID sourceId);

    /**
     * Ghi nhận chứng từ thu tiền COD một cách idempotent theo đúng đơn vị thu tiền (sourceType, sourceId).
     * Gọi lặp cùng đơn luôn trả về cùng một paymentId, không tạo chứng từ thứ 2 (TC-05, XU-04, E2E-03).
     */
    PaymentEvidence recordCodCollection(SourceType sourceType, UUID sourceId, BigDecimal collectedAmount, User admin);
}
