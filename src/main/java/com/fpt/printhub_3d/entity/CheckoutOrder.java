package com.fpt.printhub_3d.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

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

    @Size(max = 20)
    @Column(name = "order_type", length = 20)
    private String orderType; // MARKETPLACE, CUSTOM, MIXED

    @Size(max = 150)
    @Nationalized
    @Column(name = "recipient_name", length = 150)
    private String recipientName;

    @Size(max = 30)
    @Column(name = "recipient_phone", length = 30)
    private String recipientPhone;

    @Size(max = 500)
    @Nationalized
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Size(max = 30)
    @NotNull
    @ColumnDefault("'PENDING_QUOTE'")
    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING_QUOTE"; // PENDING_QUOTE, QUOTED, CONFIRMED, PAID, PREPARING, PRINTING, SHIPPING, COMPLETED, CANCELLED

    @Column(name = "items_subtotal", precision = 18, scale = 2)
    private BigDecimal itemsSubtotal;

    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "shipping_fee", precision = 18, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "discount_amount", precision = 18, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "shipping_discount_amount", precision = 18, scale = 2)
    private BigDecimal shippingDiscountAmount = BigDecimal.ZERO;

    @Column(name = "applied_subscription_id")
    private UUID appliedSubscriptionId;

    @Size(max = 200)
    @Nationalized
    @Column(name = "applied_subscription_name", length = 200)
    private String appliedSubscriptionName;

    @Column(name = "priority_production")
    private Boolean priorityProduction = false;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod; // PAYOS hoặc COD

    @NotNull
    @ColumnDefault("1")
    @Column(name = "summary_version", nullable = false)
    private Long summaryVersion = 1L;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "payment_expires_at")
    private Instant paymentExpiresAt;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
