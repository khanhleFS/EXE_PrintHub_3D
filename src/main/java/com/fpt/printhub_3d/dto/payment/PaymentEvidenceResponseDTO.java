package com.fpt.printhub_3d.dto.payment;

import com.fpt.printhub_3d.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.fpt.printhub_3d.service.PaymentEvidenceService.ZERO_AMOUNT_PROMO;

public record PaymentEvidenceResponseDTO(
        String status, String gateway, BigDecimal paidAmount, Instant paidAt, UUID paymentId, String orderCode,
        BigDecimal reconciliationAmount, Instant reconciliationReceivedAt, String reconciliationReason
) {
    public static PaymentEvidenceResponseDTO from(Payment payment, Instant zeroSettledAt) {
        if (zeroSettledAt != null) {
            return new PaymentEvidenceResponseDTO(ZERO_AMOUNT_PROMO, ZERO_AMOUNT_PROMO, BigDecimal.ZERO, zeroSettledAt,
                    null, null, null, null, null);
        }
        if (payment == null) {
            return new PaymentEvidenceResponseDTO("NONE", null, null, null, null, null, null, null, null);
        }
        boolean settled = "SUCCESS".equals(payment.getStatus()) && payment.getPaidAt() != null
                && payment.getAmount() != null && payment.getAmount().signum() > 0;
        String status = switch (payment.getStatus() != null ? payment.getStatus() : "") {
            case "PENDING" -> "PENDING";
            case "SUCCESS" -> settled ? "SETTLED" : "NONE";
            case Payment.PENDING_RECONCILIATION -> Payment.PENDING_RECONCILIATION;
            default -> "NONE";
        };
        return new PaymentEvidenceResponseDTO(status, payment.getGateway(), settled ? payment.getAmount() : null,
                settled ? payment.getPaidAt() : null, payment.getId(), payment.getTransactionId(),
                payment.getReconciliationAmount(), payment.getReconciliationReceivedAt(), payment.getReconciliationReason());
    }
}
