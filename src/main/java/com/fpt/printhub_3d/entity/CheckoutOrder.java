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

    @Size(max = 50)
    @NotNull
    @ColumnDefault("'PENDING_QUOTE'")
    @Column(name = "status", nullable = false, length = 50)
    private String status = "PENDING_QUOTE"; // PENDING_QUOTE, QUOTED, CONFIRMED, PROCESSING, COMPLETED, CANCELLED

    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "shipping_fee", precision = 18, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "marketplace_shipping_fee", precision = 18, scale = 2)
    @ColumnDefault("0")
    private BigDecimal marketplaceShippingFee = BigDecimal.ZERO;

    @Column(name = "custom_shipping_fee", precision = 18, scale = 2)
    @ColumnDefault("0")
    private BigDecimal customShippingFee = BigDecimal.ZERO;

    @Size(max = 30)
    @Column(name = "shipping_fee_assigned_scope", length = 30)
    @ColumnDefault("'MARKETPLACE'")
    private String shippingFeeAssignedScope = "MARKETPLACE";

    @Size(max = 50)
    @Column(name = "marketplace_status", length = 50)
    @ColumnDefault("'PENDING'")
    private String marketplaceStatus = "PENDING";

    @Size(max = 50)
    @Column(name = "custom_status", length = 50)
    @ColumnDefault("'PENDING_QUOTE'")
    private String customStatus = "PENDING_QUOTE";

    @Column(name = "payment_method", length = 20)
    private String paymentMethod; // PAYOS hoặc COD

    @NotNull
    @ColumnDefault("1")
    @Column(name = "summary_version", nullable = false)
    private Long summaryVersion = 1L;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "custom_pricing_version", nullable = false)
    private Long customPricingVersion = 1L;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "reward_points_awarded", nullable = false)
    private Integer rewardPointsAwarded = 0;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "payment_expires_at")
    private Instant paymentExpiresAt;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    @Size(max = 150)
    @Column(name = "idempotency_key", length = 150)
    private String idempotencyKey;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
