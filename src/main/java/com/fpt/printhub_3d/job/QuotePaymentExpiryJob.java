package com.fpt.printhub_3d.job;

import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuotePaymentExpiryJob {
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Scheduled(fixedDelay = 60000)
    public void expirePayments() {
        for (String code : paymentRepository.findExpiredQuotePaymentCodes(Instant.now())) {
            try {
                paymentService.expireQuotePayment(code);
            } catch (RuntimeException ex) {
                log.warn("Chưa xử lý được giao dịch PayOS {} hết hạn: {}", code, ex.getMessage());
            }
        }
    }
}
