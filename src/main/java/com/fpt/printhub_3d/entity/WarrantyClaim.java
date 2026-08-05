package com.fpt.printhub_3d.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "warranty_claims")
public class WarrantyClaim {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Size(max = 500)
    @Column(name = "image_proof_url", length = 500)
    private String imageProofUrl;

    @Nationalized
    @Lob
    @Column(name = "reason")
    private String reason;

    @Size(max = 20)
    @NotNull
    @ColumnDefault("'PENDING'")
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED, REPLACED

    @NotNull
    @ColumnDefault("sysutcdatetime()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @NotNull
    @ColumnDefault("sysutcdatetime()")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
