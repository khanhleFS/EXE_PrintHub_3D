package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.SubscriptionCommandRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionCommandRecordRepository extends JpaRepository<SubscriptionCommandRecord, UUID> {
    Optional<SubscriptionCommandRecord> findByRequestId(String requestId);
}

