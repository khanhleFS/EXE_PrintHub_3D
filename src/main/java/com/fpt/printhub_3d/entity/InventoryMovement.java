package com.fpt.printhub_3d.entity;

import com.fpt.printhub_3d.entity.Enumeration.MovementType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_movements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_movement_reservation_type", columnNames = {"reservation_id", "operation_type"})
})
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 20)
    private MovementType operationType;

    @NotNull
    @Column(name = "delta", nullable = false)
    private Integer delta;

    @NotNull
    @Column(name = "availableAfter", nullable = false)
    private Integer availableAfter;

    @Column(name = "actor_or_operation_id", length = 100)
    private String actorOrOperationId;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
