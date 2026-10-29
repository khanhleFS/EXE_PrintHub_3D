package com.fpt.printhub_3d.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscription_command_records", indexes = {
        @Index(name = "idx_sub_cmd_req_id", columnList = "request_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionCommandRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false, unique = true, length = 120)
    private String requestId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "command_type", nullable = false, length = 30) // GIFT, REDEEM
    private String commandType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

