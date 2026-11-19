package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.entity.EmailOutbox;
import com.fpt.printhub_3d.entity.Enumeration.EmailOutboxEventType;
import com.fpt.printhub_3d.entity.Enumeration.EmailOutboxStatus;
import com.fpt.printhub_3d.repository.EmailOutboxRepository;
import com.fpt.printhub_3d.service.EmailOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class EmailOutboxServiceImpl implements EmailOutboxService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_DATE_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final Set<String> VALID_RECEIPT_SOURCES = Set.of(
            "CHECKOUT_ORDER", "ORDER", "CUSTOM_ORDER", "SUBSCRIPTION_PURCHASE"
    );

    private final EmailOutboxRepository emailOutboxRepository;
    private final TemplateEngine templateEngine;
    private final Clock clock;
    private final String frontendBaseUrl;
    private final boolean enqueueEnabled;

    @Autowired
    public EmailOutboxServiceImpl(
            EmailOutboxRepository emailOutboxRepository,
            @Autowired(required = false) TemplateEngine templateEngine,
            @Autowired(required = false) Clock clock,
            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl,
            @Value("${app.email-outbox.enqueue-enabled:true}") boolean enqueueEnabled
    ) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.templateEngine = templateEngine;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
        this.enqueueEnabled = enqueueEnabled;
    }

    @Override
    @Transactional
    public Optional<EmailOutbox> enqueueQuotationIssued(QuotationEmailEvent event) {
        if (!enqueueEnabled || event == null || event.sourceId() == null) {
            return Optional.empty();
        }
        if (event.recipientEmail() == null || event.recipientEmail().isBlank()) {
            log.warn("Bỏ qua enqueue email báo giá do thiếu recipientEmail (sourceId={})", event.sourceId());
            return Optional.empty();
        }

        String normalizedSourceType = normalizeSourceType(event.sourceType(), "CHECKOUT_ORDER");
        String sourceIdStr = event.sourceId().toString();
        long version = Math.max(1L, event.quoteVersion());
        String eventKey = "QUOTE:" + normalizedSourceType + ":" + sourceIdStr + ":v" + version;

        // Idempotent: gửi lại cùng payload & cùng phiên bản báo giá -> giữ nguyên 1 bản ghi (EM-03)
        Optional<EmailOutbox> existing = emailOutboxRepository.findByEventKey(eventKey);
        if (existing.isPresent()) {
            log.info("Sự kiện email báo giá [{}] đã tồn tại, không tạo trùng.", eventKey);
            return existing;
        }

        Instant now = clock.instant();
        // Nếu còn thư báo giá PENDING của phiên bản cũ hơn -> đánh dấu SUPERSEDED (EM-04)
        int superseded = emailOutboxRepository.supersedeOlderPendingQuotes(
                EmailOutboxEventType.QUOTATION_ISSUED,
                normalizedSourceType,
                sourceIdStr,
                version,
                now,
                "Đã được thay thế bởi phiên bản báo giá v" + version
        );
        if (superseded > 0) {
            log.info("Đã đánh dấu SUPERSEDED cho {} email báo giá cũ của {}:{}", superseded, normalizedSourceType, sourceIdStr);
        }

        String shortCode = sourceIdStr.length() >= 8 ? sourceIdStr.substring(0, 8).toUpperCase() : sourceIdStr;
        String subject = "[PrintHub 3D] Báo giá đơn in 3D #" + shortCode + " (Phiên bản v" + version + ")";
        String actionUrl = frontendBaseUrl + "/quotations/" + sourceIdStr;
        String htmlBody = renderQuotationHtml(event, shortCode, version, actionUrl);

        EmailOutbox outbox = EmailOutbox.builder()
                .eventKey(eventKey)
                .eventType(EmailOutboxEventType.QUOTATION_ISSUED)
                .sourceType(normalizedSourceType)
                .sourceId(sourceIdStr)
                .quoteVersion(version)
                .recipientEmail(event.recipientEmail().trim())
                .subject(subject)
                .htmlBody(htmlBody)
                .idempotencyKey(eventKey)
                .status(EmailOutboxStatus.PENDING)
                .attemptCount(0)
                .nextRetryAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return Optional.of(emailOutboxRepository.save(outbox));
    }

    @Override
    @Transactional
    public Optional<EmailOutbox> enqueuePaymentReceipt(PaymentReceiptEmailEvent event) {
        if (!enqueueEnabled || event == null || event.paymentId() == null || event.sourceId() == null) {
            return Optional.empty();
        }
        if (event.recipientEmail() == null || event.recipientEmail().isBlank()) {
            log.warn("Bỏ qua enqueue biên nhận do thiếu recipientEmail (paymentId={})", event.paymentId());
            return Optional.empty();
        }
        // Không gửi biên nhận thu tiền cho đơn 0đ, đổi xu hoặc gói tặng (EM-06, BG-13)
        if (event.paidAmountVnd() == null || event.paidAmountVnd().compareTo(BigDecimal.ZERO) <= 0) {
            log.info("Bỏ qua biên nhận thanh toán vì số tiền thực thu <= 0 (paymentId={})", event.paymentId());
            return Optional.empty();
        }
        String normalizedSourceType = normalizeSourceType(event.sourceType(), "");
        if (!VALID_RECEIPT_SOURCES.contains(normalizedSourceType)) {
            log.info("Bỏ qua biên nhận thanh toán cho nguồn không thu tiền thật: {}", event.sourceType());
            return Optional.empty();
        }

        String eventKey = "RECEIPT:PAYMENT:" + event.paymentId();
        // Idempotent theo payment.id: webhook lặp hoặc verify song song chỉ tạo đúng 1 biên nhận (EM-05)
        Optional<EmailOutbox> existing = emailOutboxRepository.findByEventKey(eventKey);
        if (existing.isPresent()) {
            log.info("Biên nhận thanh toán [{}] đã tồn tại trong outbox, bỏ qua trùng lặp.", eventKey);
            return existing;
        }

        Instant now = clock.instant();
        Instant paidAt = event.paidAt() != null ? event.paidAt() : now;
        String sourceIdStr = event.sourceId().toString();
        String shortPayId = event.paymentId().toString().substring(0, 8).toUpperCase();
        String actionUrl = buildReceiptActionUrl(normalizedSourceType, sourceIdStr);

        String subject = "SUBSCRIPTION_PURCHASE".equals(normalizedSourceType)
                ? "[PrintHub 3D] Biên nhận thanh toán gói hội viên #" + shortPayId
                : "[PrintHub 3D] Biên nhận thanh toán đơn hàng #" + shortPayId;
        String htmlBody = renderReceiptHtml(event, normalizedSourceType, shortPayId, paidAt, actionUrl);

        EmailOutbox outbox = EmailOutbox.builder()
                .eventKey(eventKey)
                .eventType(EmailOutboxEventType.PAYMENT_RECEIPT)
                .sourceType(normalizedSourceType)
                .sourceId(sourceIdStr)
                .recipientEmail(event.recipientEmail().trim())
                .subject(subject)
                .htmlBody(htmlBody)
                .idempotencyKey(eventKey)
                .status(EmailOutboxStatus.PENDING)
                .attemptCount(0)
                .nextRetryAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return Optional.of(emailOutboxRepository.save(outbox));
    }

    @Override
    @Transactional(readOnly = true)
    public QueueMetrics getQueueMetrics() {
        long pending = emailOutboxRepository.countByStatus(EmailOutboxStatus.PENDING);
        long processing = emailOutboxRepository.countByStatus(EmailOutboxStatus.PROCESSING);
        long accepted = emailOutboxRepository.countByStatus(EmailOutboxStatus.PROVIDER_ACCEPTED);
        long failed = emailOutboxRepository.countByStatus(EmailOutboxStatus.FAILED_PERMANENT);
        long reconcile = emailOutboxRepository.countByStatus(EmailOutboxStatus.NEEDS_RECONCILIATION);

        Instant now = clock.instant();
        Long oldestAge = emailOutboxRepository.findFirstByStatusOrderByCreatedAtAsc(EmailOutboxStatus.PENDING)
                .map(e -> Math.max(0L, Duration.between(e.getCreatedAt(), now).getSeconds()))
                .orElse(null);

        return new QueueMetrics(pending, processing, accepted, failed, reconcile, oldestAge);
    }

    String buildReceiptActionUrl(String sourceType, String sourceId) {
        return switch (sourceType) {
            case "SUBSCRIPTION_PURCHASE" -> frontendBaseUrl + "/subscriptions";
            case "CHECKOUT_ORDER" -> frontendBaseUrl + "/orders/checkout:" + sourceId;
            case "ORDER" -> frontendBaseUrl + "/orders/product:" + sourceId;
            case "CUSTOM_ORDER" -> frontendBaseUrl + "/orders/custom:" + sourceId;
            default -> frontendBaseUrl + "/orders";
        };
    }

    private String renderQuotationHtml(QuotationEmailEvent event, String shortCode, long version, String actionUrl) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName", nonBlank(event.recipientName(), "Quý khách"));
        vars.put("orderCode", "#" + shortCode);
        vars.put("quoteVersion", "v" + version);
        vars.put("itemsSubtotal", formatVnd(event.itemsSubtotal()));
        vars.put("itemDiscountAmount", formatVnd(event.itemDiscountAmount()));
        vars.put("shippingFee", formatVnd(event.shippingFee()));
        vars.put("shippingDiscountAmount", formatVnd(event.shippingDiscountAmount()));
        vars.put("estimatedPayableAmount", formatVnd(event.estimatedPayableAmount()));
        vars.put("appliedSubscriptionName", event.appliedSubscriptionName());
        vars.put("lines", event.lines() != null ? event.lines() : List.of());
        vars.put("actionUrl", actionUrl);

        if (templateEngine != null) {
            try {
                Context ctx = new Context();
                ctx.setVariables(vars);
                return templateEngine.process("email/quotation-email", ctx);
            } catch (Exception e) {
                log.warn("Fallback HTML báo giá do lỗi render Thymeleaf: {}", e.getMessage());
            }
        }
        return "<html><body><h2>Báo giá đơn in 3D #" + shortCode + " (v" + version + ")</h2>"
                + "<p>Xin chào " + escapeHtml(nonBlank(event.recipientName(), "Quý khách")) + ",</p>"
                + "<p>Dự kiến sau ưu đãi: <strong>" + formatVnd(event.estimatedPayableAmount()) + "</strong></p>"
                + "<p><a href=\"" + actionUrl + "\">Xem chi tiết & xác nhận báo giá</a></p></body></html>";
    }

    private String renderReceiptHtml(
            PaymentReceiptEmailEvent event,
            String sourceType,
            String shortPayId,
            Instant paidAt,
            String actionUrl
    ) {
        String paidAtFormatted = VN_DATE_TIME.format(paidAt.atZone(VIETNAM_ZONE)) + " (GMT+7)";
        boolean isSub = "SUBSCRIPTION_PURCHASE".equals(sourceType);

        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName", nonBlank(event.recipientName(), "Quý khách"));
        vars.put("paymentCode", "#" + shortPayId);
        vars.put("paymentId", event.paymentId().toString());
        vars.put("sourceType", sourceType);
        vars.put("isSubscription", isSub);
        vars.put("subscriptionPlanName", event.subscriptionPlanName());
        vars.put("gateway", nonBlank(event.gateway(), "PAYOS"));
        vars.put("paidAmountVnd", formatVnd(event.paidAmountVnd()));
        vars.put("paidAt", paidAtFormatted);
        vars.put("actionUrl", actionUrl);
        vars.put("actionLabel", isSub ? "Xem gói hội viên của tôi" : "Xem chi tiết đơn hàng");

        if (templateEngine != null) {
            try {
                Context ctx = new Context();
                ctx.setVariables(vars);
                return templateEngine.process("email/payment-receipt-email", ctx);
            } catch (Exception e) {
                log.warn("Fallback HTML biên nhận do lỗi render Thymeleaf: {}", e.getMessage());
            }
        }
        return "<html><body><h2>Biên nhận thanh toán #" + shortPayId + "</h2>"
                + "<p>Số tiền thực trả: <strong>" + formatVnd(event.paidAmountVnd()) + "</strong></p>"
                + (isSub && event.subscriptionPlanName() != null
                        ? "<p>Gói hội viên: " + escapeHtml(event.subscriptionPlanName()) + "</p>" : "")
                + "<p>Thời gian thu: " + paidAtFormatted + "</p>"
                + "<p><a href=\"" + actionUrl + "\">Xem chi tiết</a></p></body></html>";
    }

    private String formatVnd(BigDecimal value) {
        BigDecimal safe = value != null ? value : BigDecimal.ZERO;
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        nf.setMaximumFractionDigits(0);
        return nf.format(safe) + "đ";
    }

    private String normalizeSourceType(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) return "http://localhost:5173";
        String s = url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private String nonBlank(String s, String fallback) {
        return s != null && !s.isBlank() ? s.trim() : fallback;
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
