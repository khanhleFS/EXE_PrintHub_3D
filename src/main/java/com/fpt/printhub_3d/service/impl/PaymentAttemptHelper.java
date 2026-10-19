package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.entity.CheckoutOrder;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.PaymentAttempt;
import com.fpt.printhub_3d.repository.PaymentAttemptRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Helper bean quản lý vòng đời PaymentAttempt và Payment trong Transaction riêng biệt (REQUIRES_NEW).
 * Giải quyết dứt điểm R2-05: Đảm bảo bản ghi theo dõi giao dịch (attempt) được persist bền vững
 * vào database trước khi gọi mạng sang PayOS, ngăn ngừa rollback bản ghi khi PayOS bị timeout/lỗi.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentAttemptHelper {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentAttempt saveInitialAttempt(
            Payment payment,
            CheckoutOrder checkoutOrder,
            Order order,
            CustomOrder customOrder,
            Long providerCode,
            BigDecimal amount,
            String scope,
            Long priceVersion,
            Instant expiry) {

        Instant now = Instant.now();

        // 1. Persist Payment trạng thái PENDING trước
        payment.setStatus("PENDING");
        payment.setTransactionId(String.valueOf(providerCode));
        payment.setAmount(amount);
        payment.setExpiresAt(expiry);
        if (payment.getCreatedAt() == null) {
            payment.setCreatedAt(now);
        }
        payment.setUpdatedAt(now);
        Payment savedPayment = paymentRepository.saveAndFlush(payment);

        // 2. Persist PaymentAttempt trạng thái PENDING
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPayment(savedPayment);
        attempt.setCheckoutOrder(checkoutOrder);
        attempt.setOrder(order);
        attempt.setCustomOrder(customOrder);
        attempt.setProviderCode(providerCode);
        attempt.setAmount(amount);
        attempt.setScope(scope != null ? scope : "ALL");
        attempt.setStatus("PENDING");
        attempt.setPriceVersion(priceVersion != null ? priceVersion : 1L);
        attempt.setCreatedAt(now);
        attempt.setExpiresAt(expiry);

        PaymentAttempt savedAttempt = paymentAttemptRepository.saveAndFlush(attempt);
        log.info("R2-05: Đã commit độc lập PaymentAttempt ID={} providerCode={} trạng thái PENDING",
                savedAttempt.getId(), providerCode);
        return savedAttempt;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttemptSuccess(Long providerCode, String checkoutUrl, String qrCode) {
        paymentAttemptRepository.findByProviderCode(providerCode).ifPresent(attempt -> {
            attempt.setCheckoutUrl(checkoutUrl);
            attempt.setQrCode(qrCode);
            paymentAttemptRepository.saveAndFlush(attempt);

            Payment payment = attempt.getPayment();
            if (payment != null) {
                payment.setCheckoutUrl(checkoutUrl);
                payment.setUpdatedAt(Instant.now());
                paymentRepository.saveAndFlush(payment);
            }
            log.info("R2-05: Đã cập nhật PayOS checkoutUrl thành công cho providerCode={}", providerCode);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttemptTimeout(Long providerCode, String failureReason) {
        paymentAttemptRepository.findByProviderCode(providerCode).ifPresent(attempt -> {
            attempt.setStatus("PENDING_VERIFY");
            paymentAttemptRepository.saveAndFlush(attempt);
            log.warn("R2-05: Đã chuyển PaymentAttempt providerCode={} sang PENDING_VERIFY: {}", providerCode, failureReason);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttemptFailed(Long providerCode, String failureReason) {
        paymentAttemptRepository.findByProviderCode(providerCode).ifPresent(attempt -> {
            attempt.setStatus("FAILED");
            paymentAttemptRepository.saveAndFlush(attempt);
            log.warn("R2-05: Đã đánh dấu PaymentAttempt providerCode={} thành FAILED: {}", providerCode, failureReason);
        });
    }
}
