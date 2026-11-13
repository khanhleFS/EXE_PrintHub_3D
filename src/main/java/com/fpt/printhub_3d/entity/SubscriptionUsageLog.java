package com.fpt.printhub_3d.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_usage_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_subscription_id", nullable = false)
    private UserSubscription userSubscription;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Size(max = 30)
    @NotNull
    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType; // ORDER, CUSTOM_ORDER, CHECKOUT_ORDER

    @NotNull
    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Size(max = 30)
    @NotNull
    @Column(name = "benefit_type", nullable = false, length = 30)
    private String benefitType; // DISCOUNT, FREE_SHIPPING, PRIORITY

    @Size(max = 20)
    @NotNull
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "RESERVED"; // RESERVED, APPLIED, RELEASED

    @Builder.Default
    @Column(name = "amount_saved", precision = 18, scale = 2)
    private BigDecimal amountSaved = BigDecimal.ZERO;

    @NotNull
    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "released_at")
    private Instant releasedAt;
}

