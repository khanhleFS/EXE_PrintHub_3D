package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.entity.CheckoutOrder;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CheckoutOrderRepository;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.service.PaymentEvidenceService;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentEvidenceServiceImpl implements PaymentEvidenceService {
    private final PaymentRepository payments;
    private final CheckoutOrderRepository checkouts;
    private final OrderRepository orders;
    private final CustomOrderRepository customs;
    private final Clock clock;
    private final SubscriptionBenefitService benefits;

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentEvidence> findSettledEvidence(SourceType sourceType, UUID sourceId) {
        if (sourceType == null || sourceId == null) {
            return Optional.empty();
        }
        Optional<Source> source = switch (sourceType) {
            case CHECKOUT_ORDER -> checkouts.findById(sourceId).map(this::checkoutSource);
            case ORDER -> orders.findById(sourceId).filter(order -> !isClosed(order.getStatus())).map(this::orderSource);
            case CUSTOM_ORDER -> customs.findById(sourceId).filter(custom -> !isClosed(custom.getStatus())).map(this::customSource);
        };
        return source.filter(target -> !isClosed(target.status()))
                .flatMap(target -> promoEvidence(target).or(() ->
                        findPayment(target).filter(payment -> isValidReceipt(payment, target))
                                .map(payment -> toEvidence(payment, target))));
    }

    @Override
    @Transactional
    public PaymentEvidence recordCodCollection(SourceType sourceType, UUID sourceId, BigDecimal collectedAmount, User admin) {
        if (admin == null || admin.getRole() != UserRole.ADMIN) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Chỉ quản trị viên được xác nhận đã thu tiền COD.");
        }
        if (sourceType == null || sourceId == null) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Nguồn ghi nhận thu tiền COD không hợp lệ.");
        }

        // Normalize child requests before taking a write lock or looking for an existing receipt.
        Optional<UUID> parentId = switch (sourceType) {
            case CHECKOUT_ORDER -> Optional.of(sourceId);
            case ORDER -> orders.findCheckoutId(sourceId);
            case CUSTOM_ORDER -> customs.findCheckoutId(sourceId);
        };
        if (parentId.isPresent()) {
            CheckoutOrder checkout = checkouts.findLockedById(parentId.get()).orElseThrow(this::sourceNotFound);
            Payment candidate = new Payment();
            candidate.setCheckoutOrder(checkout);
            return recordCod(checkoutSource(checkout), candidate, collectedAmount);
        }
        if (sourceType == SourceType.ORDER) {
            Order order = orders.findLockedById(sourceId).orElseThrow(this::sourceNotFound);
            Payment candidate = new Payment();
            candidate.setOrder(order);
            return recordCod(orderSource(order), candidate, collectedAmount);
        }
        CustomOrder custom = customs.findLockedById(sourceId).orElseThrow(this::sourceNotFound);
        return recordCod(customSource(custom), null, collectedAmount);
    }

    private PaymentEvidence recordCod(Source source, Payment newPayment, BigDecimal collectedAmount) {
        Optional<PaymentEvidence> promo = promoEvidence(source);
        if (promo.isPresent()) {
            if (collectedAmount != null && collectedAmount.signum() != 0) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT, "Đơn đã được ưu đãi toàn bộ, số tiền phải thu là 0đ.");
            }
            return promo.get();
        }
        if (newPayment == null || !"COD".equalsIgnoreCase(source.method()) || !"COMPLETED".equals(source.status())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Chỉ ghi nhận COD cho đơn hỗ trợ COD đã giao xong.");
        }
        if (collectedAmount == null || collectedAmount.signum() <= 0 || source.expectedAmount() == null
                || collectedAmount.compareTo(source.expectedAmount()) != 0) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Số tiền COD thực thu phải khớp số tiền phải thu của đơn.");
        }

        Payment payment = findPayment(source).orElse(newPayment);
        if ("SUCCESS".equals(payment.getStatus()) && "COD".equalsIgnoreCase(payment.getGateway())
                && isValidReceipt(payment, source)) {
            return toEvidence(payment, source);
        }
        if (payment != newPayment && (!"PENDING".equals(payment.getStatus())
                || !"COD".equalsIgnoreCase(payment.getGateway()))) {
            throw new ApiException(CommonErrorCode.CONFLICT, "Đơn đã có giao dịch cần kiểm tra trước khi ghi thu COD.");
        }

        Instant now = clock.instant();
        payment.setAmount(collectedAmount);
        payment.setGateway("COD");
        payment.setStatus("SUCCESS");
        payment.setPaidAt(now);
        if (payment.getCreatedAt() == null) {
            payment.setCreatedAt(now);
        }
        payment.setUpdatedAt(now);
        Payment saved = payments.save(payment);
        benefits.commitAppliedBenefits(source.type() == SourceType.CHECKOUT_ORDER
                ? SubscriptionBenefitService.UsageTargetType.CHECKOUT_ORDER
                : SubscriptionBenefitService.UsageTargetType.ORDER, source.id());
        return toEvidence(saved, source);
    }

    private boolean isValidReceipt(Payment payment, Source source) {
        return payment.isSettledReceiptFor(source.expectedAmount(), source.method());
    }

    private Source checkoutSource(CheckoutOrder checkout) {
        return new Source(SourceType.CHECKOUT_ORDER, checkout.getId(), checkout.getStatus(), checkout.getPaymentMethod(),
                checkout.getTotalAmount(), checkout.getZeroAmountSettledAt());
    }

    private Source orderSource(Order order) {
        return order.getCheckoutOrder() != null ? checkoutSource(order.getCheckoutOrder())
                : new Source(SourceType.ORDER, order.getId(), order.getStatus(), order.getPaymentMethod(), order.getTotalAmount(), null);
    }

    private Source customSource(CustomOrder custom) {
        if (custom.getCheckoutOrder() != null) {
            return checkoutSource(custom.getCheckoutOrder());
        }
        return new Source(SourceType.CUSTOM_ORDER, custom.getId(), custom.getStatus(), custom.getPaymentMethod(),
                OrderPricing.customPayable(custom), custom.getZeroAmountSettledAt());
    }

    private Optional<Payment> findPayment(Source source) {
        return switch (source.type()) {
            case CHECKOUT_ORDER -> payments.findByCheckoutOrderId(source.id());
            case ORDER -> payments.findByOrderId(source.id());
            case CUSTOM_ORDER -> payments.findByCustomOrderId(source.id());
        };
    }

    private Optional<PaymentEvidence> promoEvidence(Source source) {
        return source.zeroSettledAt() == null ? Optional.empty() : Optional.of(new PaymentEvidence(
                null, source.type(), source.id(), ZERO_AMOUNT_PROMO, BigDecimal.ZERO, source.zeroSettledAt(), true));
    }

    private PaymentEvidence toEvidence(Payment payment, Source source) {
        return new PaymentEvidence(payment.getId(), source.type(), source.id(), payment.getGateway(),
                payment.getAmount(), payment.getPaidAt(), false);
    }

    private boolean isClosed(String status) {
        return "CANCELLED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status);
    }

    private ApiException sourceNotFound() {
        return new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nguồn thanh toán.");
    }

    private record Source(SourceType type, UUID id, String status, String method,
                          BigDecimal expectedAmount, Instant zeroSettledAt) {}
}
