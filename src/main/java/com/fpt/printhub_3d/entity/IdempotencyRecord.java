package com.fpt.printhub_3d.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
        @UniqueConstraint(name = "uq_idempotency_user_action_key", columnNames = {"user_id", "action_key", "idempotency_key"})
}, indexes = {
        @Index(name = "idx_idempotency_lookup", columnList = "user_id, action_key, idempotency_key")
})
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Size(max = 100)
    @Column(name = "action_key", nullable = false, length = 100)
    private String actionKey;

    @NotNull
    @Size(max = 150)
    @Column(name = "idempotency_key", nullable = false, length = 150)
    private String idempotencyKey;

    @Size(max = 100)
    @Column(name = "request_hash", length = 100)
    private String requestHash;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @NotNull
    @Size(max = 30)
    @ColumnDefault("'PROCESSING'")
    @Column(name = "status", nullable = false, length = 30)
    private String status = "PROCESSING"; // PROCESSING, COMPLETED, FAILED

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt = Instant.now().plus(java.time.Duration.ofHours(24));
}
