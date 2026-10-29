package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.SubscriptionPurchase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPurchaseRepository extends JpaRepository<SubscriptionPurchase, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sp FROM SubscriptionPurchase sp WHERE sp.id = :id")
    Optional<SubscriptionPurchase> findLockedById(@Param("id") UUID id);

    List<SubscriptionPurchase> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<SubscriptionPurchase> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM SubscriptionPurchase sp WHERE sp.status = 'PAID' AND sp.settledAt >= :from AND sp.settledAt <= :to")
    BigDecimal sumSettledRevenueBetween(@Param("from") Instant from, @Param("to") Instant to);
}

