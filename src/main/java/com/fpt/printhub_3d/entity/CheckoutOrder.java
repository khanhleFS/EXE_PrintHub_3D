package com.fpt.printhub_3d.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "checkout_orders")
public class CheckoutOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    @Size(max = 30)
    @Column(name = "shipping_mode", length = 30, nullable = false)
    @ColumnDefault("'COMMON'")
    private String shippingMode = "COMMON"; // COMMON (Giao chung) hoặc SEPARATE (Giao riêng)

    @Size(max = 30)
    @NotNull
    @ColumnDefault("'PENDING_QUOTE'")
    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING_QUOTE"; // PENDING_QUOTE, QUOTED, CONFIRMED, PROCESSING, COMPLETED, CANCELLED

    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "shipping_fee", precision = 18, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod; // PAYOS hoặc COD

    @NotNull
    @ColumnDefault("1")
    @Column(name = "summary_version", nullable = false)
    private Long summaryVersion = 1L;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
