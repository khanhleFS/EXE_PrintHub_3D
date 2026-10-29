package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Enumeration.MovementType;
import com.fpt.printhub_3d.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    List<InventoryMovement> findByReservationId(UUID reservationId);

    boolean existsByReservationIdAndOperationType(UUID reservationId, MovementType operationType);
}
