package com.fpt.printhub_3d.entity;

import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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

    @Column(name = "description", length = 255)
    private String description; // Ví dụ: "Tích điểm từ đơn hàng #ORD123"

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
