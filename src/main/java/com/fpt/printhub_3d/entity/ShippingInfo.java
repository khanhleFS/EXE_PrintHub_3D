package com.fpt.printhub_3d.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "shipping_infos")
public class ShippingInfo {
    @Id
    @Column(name = "order_id", nullable = false)
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "order_id", nullable = false)
    private Order orders;

    @Size(max = 150)
    @NotNull
    @Nationalized
    @Column(name = "recipient_name", nullable = false, length = 150)
    private String recipientName;

    @Size(max = 20)
    @NotNull
    @Nationalized
    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Size(max = 255)
    @NotNull
    @Nationalized
    @Column(name = "address", nullable = false)
    private String address;

    @Size(max = 100)
    @NotNull
    @Nationalized
    @Column(name = "province", nullable = false, length = 100)
    private String province;

    @Size(max = 100)
    @Nationalized
    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "shipped_at")
    private Instant shippedAt;

}