package com.fpt.printhub_3d.entity;

import com.fpt.printhub_3d.common.infrastructure.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UserSubscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private SubscriptionPlan plan;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;
    
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "duration_days")
    @Builder.Default
    private Integer durationDays = 30;

    @Column(name = "applied_price", precision = 18, scale = 2)
    private java.math.BigDecimal appliedPrice;

    @Column(name = "applied_required_points")
    private Integer appliedRequiredPoints;

    @Column(name = "applied_free_shipping")
    @Builder.Default
    private Boolean appliedFreeShipping = false;

    @Column(name = "applied_shipping_discount_amount", precision = 18, scale = 2)
    private java.math.BigDecimal appliedShippingDiscountAmount;

    @Column(name = "activation_source", length = 50)
    private String activationSource;
}
