package com.fpt.printhub_3d.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "custom_orders")
public class CustomOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maker_id")
    private User maker;

    @Size(max = 100)
    @Column(name = "ruler_model", length = 100)
    private String rulerModel;

    @Size(max = 100)
    @Nationalized
    @Column(name = "custom_name", length = 100)
    private String customName;

    @Size(max = 50)
    @Column(name = "custom_student_id", length = 50)
    private String customStudentId;

    @Size(max = 50)
    @Column(name = "color", length = 50)
    private String color;

    @Size(max = 50)
    @Column(name = "font_style", length = 50)
    private String fontStyle;

    @Column(name = "quantity")
    private Integer quantity = 1;

    @Nationalized
    @Lob
    @Column(name = "requirements")
    private String requirements;

    @Size(max = 500)
    @Nationalized
    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @Column(name = "quoted_price", precision = 18, scale = 2)
    private BigDecimal quotedPrice;

    @Size(max = 20)
    @NotNull
    @ColumnDefault("'PENDING'")
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}