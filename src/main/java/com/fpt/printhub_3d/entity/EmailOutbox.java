package com.fpt.printhub_3d.entity;

import com.fpt.printhub_3d.entity.Enumeration.EmailOutboxEventType;
import com.fpt.printhub_3d.entity.Enumeration.EmailOutboxStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;
import java.util.UUID;

/**
 * Bảng Transactional Outbox cho Email báo giá & biên nhận thanh toán (Luồng 07).
 * Đảm bảo commit/rollback nguyên tử cùng nghiệp vụ nguồn và cố định nội dung + Idempotency-Key
 * qua mọi lần gửi lại sau lỗi 429/5xx/timeout (EM-01..EM-12).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "email_outbox",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_email_outbox_event_key", columnNames = "event_key")
        },
        indexes = {
                @Index(name = "idx_email_outbox_status_next_retry", columnList = "status, next_retry_at"),
                @Index(name = "idx_email_outbox_source", columnList = "source_type, source_id")
        }
)
public class EmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_key", nullable = false, unique = true, length = 180)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private EmailOutboxEventType eventType;

    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 100)
    private String sourceId;

    @Column(name = "quote_version")
    private Long quoteVersion;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Nationalized
    @Column(name = "subject", nullable = false, length = 300)
    private String subject;

    @Nationalized
    @Column(name = "html_body", nullable = false, columnDefinition = "TEXT")
    private String htmlBody;

    @Column(name = "idempotency_key", nullable = false, length = 180)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EmailOutboxStatus status;

    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "first_attempt_at")
    private Instant firstAttemptAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "lease_owner", length = 120)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Nationalized
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
