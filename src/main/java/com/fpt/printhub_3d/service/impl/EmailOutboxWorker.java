package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.entity.EmailOutbox;
import com.fpt.printhub_3d.entity.Enumeration.EmailOutboxStatus;
import com.fpt.printhub_3d.repository.EmailOutboxRepository;
import com.fpt.printhub_3d.service.MailProviderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Worker xử lý hàng đợi email_outbox theo mô hình Claim (Tx ngắn) -> HTTP Resend (ngoài Tx) -> Commit kết quả (Tx ngắn).
 * Hỗ trợ:
 * - FOR UPDATE SKIP LOCKED + lease để nhiều worker không gửi đúp (EM-10)
 * - Exponential backoff cho 429/5xx/lỗi mạng tạm thời (EM-08)
 * - Tôn trọng cửa sổ Idempotency-Key 24h của Resend khi timeout chưa rõ kết quả; vượt 24h chuyển NEEDS_RECONCILIATION (EM-09)
 * - Lỗi vĩnh viễn chuyển FAILED_PERMANENT mà không chặn các thư sau (EM-10)
 */
@Slf4j
@Component
public class EmailOutboxWorker {

    public static final Duration RESEND_IDEMPOTENCY_WINDOW = Duration.ofHours(24);
    private static final long[] BACKOFF_SECONDS = {30L, 120L, 600L, 1800L, 7200L};

    private final EmailOutboxRepository emailOutboxRepository;
    private final MailProviderClient mailProviderClient;
    private final Clock clock;
    private final TransactionTemplate txTemplate;
    private final boolean enabled;
    private final int batchSize;
    private final long leaseSeconds;
    private final String defaultWorkerId;

    @Autowired
    public EmailOutboxWorker(
            EmailOutboxRepository emailOutboxRepository,
            MailProviderClient mailProviderClient,
            @Autowired(required = false) Clock clock,
            @Autowired(required = false) PlatformTransactionManager transactionManager,
            @Value("${app.email-outbox.enabled:false}") boolean enabled,
            @Value("${app.email-outbox.batch-size:20}") int batchSize,
            @Value("${app.email-outbox.lease-seconds:60}") long leaseSeconds
    ) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.mailProviderClient = mailProviderClient;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.txTemplate = transactionManager != null ? new TransactionTemplate(transactionManager) : null;
        this.enabled = enabled;
        this.batchSize = Math.max(1, batchSize);
        this.leaseSeconds = Math.max(10L, leaseSeconds);
        this.defaultWorkerId = "email-worker-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Scheduled(fixedDelayString = "${app.email-outbox.poll-delay-ms:10000}")
    public void scheduledPoll() {
        if (!enabled) {
            return;
        }
        processBatch(defaultWorkerId);
    }

    /**
     * Claim tối đa batchSize thư đến hạn cho workerId chỉ định và gửi từng thư độc lập.
     * Mỗi lỗi của 1 thư không bao giờ chặn các thư còn lại trong batch.
     */
    public int processBatch(String workerId) {
        String effectiveWorker = (workerId != null && !workerId.isBlank()) ? workerId : defaultWorkerId;
        List<EmailOutbox> claimed = runInTransaction(() -> claimDueBatch(effectiveWorker));
        if (claimed == null || claimed.isEmpty()) {
            return 0;
        }

        int processed = 0;
        for (EmailOutbox entry : claimed) {
            processed++;
            try {
                // Gọi HTTP ra Resend NGOÀI database transaction để không giữ lock/connection
                MailProviderClient.MailSendResult result = mailProviderClient.sendHtml(
                        entry.getRecipientEmail(),
                        entry.getSubject(),
                        entry.getHtmlBody(),
                        entry.getIdempotencyKey()
                );
                runInTransaction(() -> {
                    applyOutcome(entry.getId(), effectiveWorker, result);
                    return null;
                });
            } catch (Exception ex) {
                log.error("Lỗi không mong đợi khi xử lý outbox {}: {}", entry.getId(), ex.getMessage(), ex);
                runInTransaction(() -> {
                    applyOutcome(entry.getId(), effectiveWorker,
                            MailProviderClient.MailSendResult.retryable(null, ex.getMessage()));
                    return null;
                });
            }
        }
        return processed;
    }

    private List<EmailOutbox> claimDueBatch(String workerId) {
        Instant now = clock.instant();
        List<EmailOutbox> due = emailOutboxRepository.lockDueEntries(now, batchSize);
        if (due.isEmpty()) {
            return List.of();
        }
        Instant leaseUntil = now.plusSeconds(leaseSeconds);
        for (EmailOutbox entry : due) {
            entry.setStatus(EmailOutboxStatus.PROCESSING);
            entry.setLeaseOwner(workerId);
            entry.setLeaseUntil(leaseUntil);
            if (entry.getFirstAttemptAt() == null) {
                entry.setFirstAttemptAt(now);
            }
            entry.setUpdatedAt(now);
        }
        return emailOutboxRepository.saveAll(due);
    }

    private void applyOutcome(UUID entryId, String workerId, MailProviderClient.MailSendResult result) {
        EmailOutbox entry = emailOutboxRepository.findById(entryId).orElse(null);
        if (entry == null) {
            return;
        }
        // Bảo vệ lease: nếu worker khác đã thu hồi sau khi quá hạn lease thì không ghi đè
        if (entry.getStatus() != EmailOutboxStatus.PROCESSING
                || (entry.getLeaseOwner() != null && !entry.getLeaseOwner().equals(workerId))) {
            return;
        }

        Instant now = clock.instant();
        int nextAttempt = (entry.getAttemptCount() != null ? entry.getAttemptCount() : 0) + 1;
        entry.setAttemptCount(nextAttempt);
        entry.setLeaseOwner(null);
        entry.setLeaseUntil(null);
        entry.setUpdatedAt(now);

        if (result == null) {
            scheduleRetry(entry, nextAttempt, now, "Không nhận được phản hồi từ MailProviderClient");
            emailOutboxRepository.save(entry);
            return;
        }

        switch (result.outcome()) {
            case ACCEPTED -> {
                entry.setStatus(EmailOutboxStatus.PROVIDER_ACCEPTED);
                entry.setProviderMessageId(result.providerMessageId());
                entry.setSentAt(now);
                entry.setNextRetryAt(null);
                entry.setLastError(null);
            }
            case PERMANENT_ERROR -> {
                entry.setStatus(EmailOutboxStatus.FAILED_PERMANENT);
                entry.setNextRetryAt(null);
                entry.setLastError(truncate(result.errorMessage(), 2000));
            }
            case TIMEOUT_AMBIGUOUS -> {
                Instant anchor = entry.getFirstAttemptAt() != null ? entry.getFirstAttemptAt() : entry.getCreatedAt();
                boolean withinWindow = anchor != null && !now.isAfter(anchor.plus(RESEND_IDEMPOTENCY_WINDOW));
                if (withinWindow) {
                    // Trong cửa sổ 24h của Resend: retry an toàn bằng cùng Idempotency-Key & cùng nội dung (EM-09)
                    scheduleRetry(entry, nextAttempt, now, result.errorMessage());
                } else {
                    // Đã vượt 24h: không tự gửi lại để tránh gửi đôi, chuyển chờ đối soát (EM-09)
                    entry.setStatus(EmailOutboxStatus.NEEDS_RECONCILIATION);
                    entry.setNextRetryAt(null);
                    entry.setLastError(truncate("Vượt cửa sổ Idempotency-Key 24h sau timeout: " + result.errorMessage(), 2000));
                }
            }
            case RETRYABLE_ERROR -> scheduleRetry(entry, nextAttempt, now, result.errorMessage());
        }

        emailOutboxRepository.save(entry);
    }

    private void scheduleRetry(EmailOutbox entry, int attemptCount, Instant now, String errorMessage) {
        int idx = Math.min(Math.max(0, attemptCount - 1), BACKOFF_SECONDS.length - 1);
        long delaySec = BACKOFF_SECONDS[idx];
        entry.setStatus(EmailOutboxStatus.PENDING);
        entry.setNextRetryAt(now.plusSeconds(delaySec));
        entry.setLastError(truncate(errorMessage, 2000));
    }

    private <T> T runInTransaction(java.util.function.Supplier<T> action) {
        if (txTemplate == null) {
            return action.get();
        }
        return txTemplate.execute(status -> action.get());
    }

    private String truncate(String val, int maxLen) {
        if (val == null) return null;
        return val.length() <= maxLen ? val : val.substring(0, maxLen);
    }
}
