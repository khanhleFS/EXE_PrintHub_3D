package com.fpt.printhub_3d.entity;

import com.fpt.printhub_3d.entity.Enumeration.OrderRewardStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Bản ghi chống thưởng trùng theo khóa duy nhất (source_type, source_id, reward_type) (XU-01..XU-09).
 * Chuẩn hóa mọi đơn thuộc checkout cha về source_type = CHECKOUT_ORDER để không bao giờ cộng xu lần hai.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "order_reward_records",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_order_reward_source_type",
                        columnNames = {"source_type", "source_id", "reward_type"}
                )
        },
        indexes = {
                @Index(name = "idx_order_reward_status", columnList = "status"),
                @Index(name = "idx_order_reward_user", columnList = "user_id")
        }
)
public class OrderRewardRecord {

    public static final String REWARD_TYPE_COMPLETION = "ORDER_COMPLETION";
    public static final String REWARD_TYPE_BACKFILL = "COMPLETION_BACKFILL";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType; // CHECKOUT_ORDER | ORDER | CUSTOM_ORDER

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "reward_type", nullable = false, length = 40)
    private String rewardType; // ORDER_COMPLETION | COMPLETION_BACKFILL

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "eligible_net_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal eligibleNetAmount;

    @Column(name = "points_awarded", nullable = false)
    private Integer pointsAwarded;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderRewardStatus status;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "point_transaction_id")
    private Long pointTransactionId;

    @Nationalized
    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
