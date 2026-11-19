package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.entity.EmailOutbox;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hợp đồng Transactional Outbox cho Email báo giá & biên nhận thanh toán (Luồng 07).
 * BE 1 chỉ cần gọi các method enqueue* bên trong cùng @Transactional của nghiệp vụ báo giá / quyết toán thanh toán.
 */
public interface EmailOutboxService {

    record QuotationLineItem(
            String designName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}

    record QuotationEmailEvent(
            String sourceType, // CHECKOUT_ORDER | CUSTOM_ORDER
            UUID sourceId,
            long quoteVersion,
            String recipientEmail,
            String recipientName,
            BigDecimal itemsSubtotal,
            BigDecimal itemDiscountAmount,
            BigDecimal shippingFee,
            BigDecimal shippingDiscountAmount,
            BigDecimal estimatedPayableAmount,
            String appliedSubscriptionName,
            List<QuotationLineItem> lines
    ) {}

    record PaymentReceiptEmailEvent(
            UUID paymentId,
            String sourceType, // CHECKOUT_ORDER | ORDER | CUSTOM_ORDER | SUBSCRIPTION_PURCHASE
            UUID sourceId,
            String recipientEmail,
            String recipientName,
            BigDecimal paidAmountVnd,
            String gateway, // PAYOS | COD
            Instant paidAt,
            String subscriptionPlanName
    ) {}

    record QueueMetrics(
            long pendingCount,
            long processingCount,
            long providerAcceptedCount,
            long failedPermanentCount,
            long needsReconciliationCount,
            Long oldestPendingAgeSeconds
    ) {}

    /**
     * Ghi sự kiện email báo giá đủ dòng vào outbox (nằm chung transaction gọi).
     * Tự động supersede các thư báo giá PENDING của phiên bản cũ hơn và idempotent theo (sourceType, sourceId, quoteVersion).
     */
    Optional<EmailOutbox> enqueueQuotationIssued(QuotationEmailEvent event);

    /**
     * Ghi sự kiện email biên nhận thanh toán vào outbox theo paymentId duy nhất.
     * Bỏ qua an toàn nếu đơn 0đ, đổi xu, gói tặng hoặc không hợp lệ.
     */
    Optional<EmailOutbox> enqueuePaymentReceipt(PaymentReceiptEmailEvent event);

    QueueMetrics getQueueMetrics();
}
