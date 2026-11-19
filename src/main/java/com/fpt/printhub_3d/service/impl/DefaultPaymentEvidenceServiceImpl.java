package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.entity.CheckoutOrder;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CheckoutOrderRepository;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.service.PaymentEvidenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Fallback mặc định của PaymentEvidenceService đọc từ PaymentRepository hiện tại.
 * Cho phép BE 2 triển khai & kiểm thử độc lập 100%; sẽ tự nhường chỗ khi BE 1 cung cấp bean PaymentEvidenceService chính thức.
 */
@Service
@ConditionalOnMissingBean(PaymentEvidenceService.class)
public class DefaultPaymentEvidenceServiceImpl implements PaymentEvidenceService {

    private final PaymentRepository paymentRepository;
    private final CheckoutOrderRepository checkoutOrderRepository;
    private final OrderRepository orderRepository;
    private final CustomOrderRepository customOrderRepository;
    private final Clock clock;

    @Autowired
    public DefaultPaymentEvidenceServiceImpl(
            PaymentRepository paymentRepository,
            CheckoutOrderRepository checkoutOrderRepository,
            OrderRepository orderRepository,
            CustomOrderRepository customOrderRepository,
            @Autowired(required = false) Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.checkoutOrderRepository = checkoutOrderRepository;
        this.orderRepository = orderRepository;
        this.customOrderRepository = customOrderRepository;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentEvidence> findSettledEvidence(SourceType sourceType, UUID sourceId) {
        if (sourceType == null || sourceId == null) {
            return Optional.empty();
        }
        Optional<Payment> paymentOpt = findPayment(sourceType, sourceId);
        if (paymentOpt.isPresent()) {
            Payment p = paymentOpt.get();
            if ("SUCCESS".equalsIgnoreCase(p.getStatus())) {
                return Optional.of(new PaymentEvidence(
                        p.getId(),
                        sourceType,
                        sourceId,
                        p.getGateway(),
                        p.getAmount(),
                        p.getPaidAt() != null ? p.getPaidAt() : p.getUpdatedAt(),
                        false
                ));
            }
        }
        return Optional.empty();
    }

    @Override
    @Transactional
    public PaymentEvidence recordCodCollection(SourceType sourceType, UUID sourceId, BigDecimal collectedAmount, User admin) {
        if (sourceType == null || sourceId == null) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Nguồn ghi nhận thu tiền COD không hợp lệ");
        }
        Instant now = clock.instant();
        Optional<Payment> existingOpt = findPayment(sourceType, sourceId);
        if (existingOpt.isPresent() && "SUCCESS".equalsIgnoreCase(existingOpt.get().getStatus())) {
            Payment existing = existingOpt.get();
            return new PaymentEvidence(
                    existing.getId(),
                    sourceType,
                    sourceId,
                    existing.getGateway(),
                    existing.getAmount(),
                    existing.getPaidAt() != null ? existing.getPaidAt() : existing.getUpdatedAt(),
                    false
            );
        }

        Payment codPayment = existingOpt.orElseGet(Payment::new);
        BigDecimal resolvedAmount = collectedAmount != null ? collectedAmount : BigDecimal.ZERO;

        switch (sourceType) {
            case CHECKOUT_ORDER -> {
                CheckoutOrder co = checkoutOrderRepository.findLockedById(sourceId)
                        .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy CheckoutOrder " + sourceId));
                codPayment.setCheckoutOrder(co);
                if (collectedAmount == null && co.getTotalAmount() != null) {
                    resolvedAmount = co.getTotalAmount();
                }
            }
            case ORDER -> {
                Order order = orderRepository.findLockedById(sourceId)
                        .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy Order " + sourceId));
                codPayment.setOrder(order);
                if (collectedAmount == null && order.getTotalAmount() != null) {
                    resolvedAmount = order.getTotalAmount();
                }
            }
            case CUSTOM_ORDER -> {
                CustomOrder custom = customOrderRepository.findLockedById(sourceId)
                        .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy CustomOrder " + sourceId));
                codPayment.setCustomOrder(custom);
                if (collectedAmount == null && custom.getQuotedPrice() != null) {
                    resolvedAmount = custom.getQuotedPrice();
                }
            }
        }

        codPayment.setAmount(resolvedAmount);
        codPayment.setGateway("COD");
        codPayment.setStatus("SUCCESS");
        codPayment.setPaidAt(now);
        if (codPayment.getCreatedAt() == null) {
            codPayment.setCreatedAt(now);
        }
        codPayment.setUpdatedAt(now);
        Payment saved = paymentRepository.save(codPayment);

        return new PaymentEvidence(
                saved.getId(),
                sourceType,
                sourceId,
                saved.getGateway(),
                saved.getAmount(),
                saved.getPaidAt(),
                false
        );
    }

    private Optional<Payment> findPayment(SourceType sourceType, UUID sourceId) {
        return switch (sourceType) {
            case CHECKOUT_ORDER -> paymentRepository.findByCheckoutOrderId(sourceId);
            case ORDER -> paymentRepository.findByOrderId(sourceId);
            case CUSTOM_ORDER -> paymentRepository.findByCustomOrderId(sourceId);
        };
    }
}
