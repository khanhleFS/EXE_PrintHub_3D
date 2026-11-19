package com.fpt.printhub_3d.entity;

import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "point_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_wallet_id")
    private PointWallet pointWallet;

    @Column(name = "amount", nullable = false)
    private Integer amount; // Số điểm biến động

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50, nullable = false)
    private PointTransactionType type;

    @Column(name = "description", length = 320)
    private String description; // Ví dụ: "Tích điểm từ đơn hàng #ORD123"

    @Column(name = "source_type", length = 40)
    private String sourceType; // CHECKOUT_ORDER, ORDER, CUSTOM_ORDER, ORDER_ITEM, SUBSCRIPTION_REDEEM

    @Column(name = "source_id", length = 100)
    private String sourceId; // UUID đơn hàng, ID dòng orderItem, hoặc ID subscription

    @Column(name = "reward_type", length = 40)
    private String rewardType; // ORDER_COMPLETION, COMPLETION_BACKFILL, REVIEW_PHOTO_BONUS, SUBSCRIPTION_REDEEM

    @Column(name = "reference_plan_id")
    private UUID referencePlanId;

    @Nationalized
    @Column(name = "reference_plan_name", length = 255)
    private String referencePlanName;

    @Column(name = "reference_subscription_id")
    private UUID referenceSubscriptionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "occurred_at", updatable = false)
    private Instant occurredAt;
}
