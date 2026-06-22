package com.fpt.printhub_3d.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "maker_profiles")
public class MakerProfile {
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false)
    private User users;

    @Size(max = 150)
    @Nationalized
    @Column(name = "business_name", length = 150)
    private String businessName;

    @Nationalized
    @Lob
    @Column(name = "equipment_info")
    private String equipmentInfo;

    @Size(max = 500)
    @Nationalized
    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    @Nationalized
    @Lob
    @Column(name = "bio")
    private String bio;

    @Size(max = 20)
    @NotNull
    @ColumnDefault("'PENDING'")
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "trust_score", nullable = false)
    private Double trustScore;

    @Column(name = "verified_at")
    private Instant verifiedAt;

}