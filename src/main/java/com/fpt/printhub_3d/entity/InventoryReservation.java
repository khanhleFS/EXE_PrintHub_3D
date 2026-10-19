package com.fpt.printhub_3d.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "inventory_reservations", indexes = {
        @Index(name = "idx_inv_res_product_status", columnList = "product_id, status, expires_at"),
        @Index(name = "idx_inv_res_checkout", columnList = "checkout_order_id")
})
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_order_id")
    private CheckoutOrder checkoutOrder;

    @NotNull
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @NotNull
    @Size(max = 50)
    @ColumnDefault("'RESERVED'")
    @Column(name = "status", nullable = false, length = 50)
    private String status = "RESERVED"; // RESERVED, COMMITTED, RELEASED, EXPIRED, RECONCILIATION_REQUIRED

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
