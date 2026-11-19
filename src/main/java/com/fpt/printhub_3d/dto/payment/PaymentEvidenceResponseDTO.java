package com.fpt.printhub_3d.dto.payment;

import java.math.BigDecimal;
import java.time.Instant;

import static com.fpt.printhub_3d.service.PaymentEvidenceService.ZERO_AMOUNT_PROMO;

public record PaymentEvidenceResponseDTO(String status, String gateway, BigDecimal paidAmount, Instant paidAt) {
    public static PaymentEvidenceResponseDTO forZeroAmountPromo(Instant settledAt) {
        return settledAt == null ? null
                : new PaymentEvidenceResponseDTO(ZERO_AMOUNT_PROMO, ZERO_AMOUNT_PROMO, BigDecimal.ZERO, settledAt);
    }
}
