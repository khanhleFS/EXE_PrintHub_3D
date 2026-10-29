package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Enumeration.ReservationState;
import com.fpt.printhub_3d.entity.InventoryReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {

    List<InventoryReservation> findByOrderId(UUID orderId);

    Optional<InventoryReservation> findByOrderIdAndProductId(UUID orderId, UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM InventoryReservation r WHERE r.orderId = :orderId")
    List<InventoryReservation> findLockedByOrderId(@Param("orderId") UUID orderId);

    List<InventoryReservation> findByStateAndExpiresAtBefore(ReservationState state, Instant cutoff);
}
