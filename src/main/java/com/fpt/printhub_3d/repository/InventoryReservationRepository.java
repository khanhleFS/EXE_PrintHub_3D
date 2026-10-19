package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.InventoryReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {

    List<InventoryReservation> findByCheckoutOrderId(UUID checkoutOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM InventoryReservation r WHERE r.checkoutOrder.id = :checkoutOrderId")
    List<InventoryReservation> findLockedByCheckoutOrderId(@Param("checkoutOrderId") UUID checkoutOrderId);

    @Query("SELECT COALESCE(SUM(r.quantity), 0) FROM InventoryReservation r " +
            "WHERE r.product.id = :productId AND r.status = 'RESERVED' AND r.expiresAt > :now")
    Integer getActiveReservedQuantity(@Param("productId") UUID productId, @Param("now") Instant now);
}
