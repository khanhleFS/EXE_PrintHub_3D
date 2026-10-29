package com.fpt.printhub_3d.entity;

import com.fpt.printhub_3d.entity.Enumeration.ReservationState;
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
@Table(name = "inventory_reservations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reservation_order_product", columnNames = {"order_id", "product_id"})
})
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(name = "version")
    private Long version;

    @NotNull
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @NotNull
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @NotNull
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private ReservationState state;

    @NotNull
    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "source", length = 30)
    private String source;

    @Column(name = "release_reason", length = 255)
    private String releaseReason;

    @Column(name = "operation_id", length = 100)
    private String operationId;
}
