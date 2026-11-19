package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.EmailOutbox;
import com.fpt.printhub_3d.entity.Enumeration.EmailOutboxEventType;
import com.fpt.printhub_3d.entity.Enumeration.EmailOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    Optional<EmailOutbox> findByEventKey(String eventKey);

    List<EmailOutbox> findBySourceTypeAndSourceIdOrderByCreatedAtAsc(String sourceType, String sourceId);

    long countByStatus(EmailOutboxStatus status);

    Optional<EmailOutbox> findFirstByStatusOrderByCreatedAtAsc(EmailOutboxStatus status);

    @Modifying
    @Query("""
            UPDATE EmailOutbox e
            SET e.status = com.fpt.printhub_3d.entity.Enumeration.EmailOutboxStatus.SUPERSEDED,
                e.updatedAt = :now,
                e.lastError = :reason
            WHERE e.eventType = :eventType
              AND e.sourceType = :sourceType
              AND e.sourceId = :sourceId
              AND e.status = com.fpt.printhub_3d.entity.Enumeration.EmailOutboxStatus.PENDING
              AND (e.quoteVersion IS NULL OR e.quoteVersion < :newVersion)
            """)
    int supersedeOlderPendingQuotes(
            @Param("eventType") EmailOutboxEventType eventType,
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("newVersion") Long newVersion,
            @Param("now") Instant now,
            @Param("reason") String reason
    );

    /**
     * Claim batch an toàn giữa nhiều worker chạy song song trên PostgreSQL bằng FOR UPDATE SKIP LOCKED (EM-10).
     * Đồng thời thu hồi các bản ghi PROCESSING bị treo đã quá hạn lease_until.
     */
    @Query(value = """
            SELECT * FROM email_outbox
            WHERE (status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= :now))
               OR (status = 'PROCESSING' AND lease_until IS NOT NULL AND lease_until < :now)
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<EmailOutbox> lockDueEntries(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
