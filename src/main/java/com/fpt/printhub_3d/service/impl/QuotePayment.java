package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class QuotePayment {
    private static final BigDecimal PAYOS_MIN_AMOUNT = new BigDecimal("2000");

    private QuotePayment() {}

    static void prepare(PaymentRepository repository, Payment payment, BigDecimal amount) {
        requirePayable(amount);
        Instant now = Instant.now();
        payment.setAmount(amount);
        payment.setGateway("PAYOS");
        payment.setStatus("PENDING");
        payment.setTransactionId(String.valueOf(nextOrderCode(repository)));
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        payment.setExpiresAt(now.plusSeconds(900));
        repository.save(payment);
    }

    static void requirePayable(BigDecimal amount) {
        if (amount == null || amount.compareTo(PAYOS_MIN_AMOUNT) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền PayOS phải tối thiểu 2.000 VND.");
        }
        try {
            amount.longValueExact();
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền PayOS phải là số nguyên VND hợp lệ.");
        }
    }

    static long nextOrderCode(PaymentRepository repository) {
        long code;
        do {
            code = (UUID.randomUUID().getMostSignificantBits() & 0x1FFFFFFFFFFFFEL) + 1;
        } while (repository.findByTransactionId(Long.toString(code)).isPresent());
        return code;
    }
}
