package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.CheckoutOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckoutOrderRepository extends JpaRepository<CheckoutOrder, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CheckoutOrder c WHERE c.id = :id")
    Optional<CheckoutOrder> findLockedById(@Param("id") UUID id);

    List<CheckoutOrder> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<CheckoutOrder> findFirstByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
