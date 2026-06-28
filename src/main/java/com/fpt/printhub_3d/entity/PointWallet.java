package com.fpt.printhub_3d.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "point_wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointWallet {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Builder.Default
    @Column(name = "balance", nullable = false)
    private Integer balance = 0; // Số dư điểm thưởng

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
