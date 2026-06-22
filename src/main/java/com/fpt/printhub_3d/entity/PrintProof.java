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

@Getter
@Setter
@Entity
@Table(name = "print_proofs")
public class PrintProof {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "custom_order_id", nullable = false)
    private CustomOrder customOrder;

    @Size(max = 500)
    @Nationalized
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Size(max = 500)
    @Nationalized
    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Nationalized
    @Lob
    @Column(name = "note")
    private String note;

    @NotNull
    @ColumnDefault("sysutcdatetime()")
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

}