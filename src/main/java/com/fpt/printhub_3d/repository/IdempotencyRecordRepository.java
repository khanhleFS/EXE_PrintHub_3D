package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByUserIdAndActionKeyAndIdempotencyKey(
            UUID userId, String actionKey, String idempotencyKey);
}
