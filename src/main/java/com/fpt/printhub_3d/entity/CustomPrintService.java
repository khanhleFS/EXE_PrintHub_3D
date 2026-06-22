package com.fpt.printhub_3d.entity;

import com.fpt.printhub_3d.common.infrastructure.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "custom_print_services")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CustomPrintService extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maker_id", nullable = false)
    private User maker;

    @NotBlank
    @Nationalized
    @Column(name = "service_name", nullable = false, length = 200)
    private String serviceName;

    @Nationalized
    @Lob
    @Column(name = "description")
    private String description;

    @ElementCollection
    @CollectionTable(
            name = "custom_print_service_printers",
            joinColumns = @JoinColumn(name = "service_id")
    )
    @Column(name = "printer_model", length = 200)
    @Nationalized
    @Builder.Default
    private List<String> printerModels = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "custom_print_service_materials",
            joinColumns = @JoinColumn(name = "service_id")
    )
    @Column(name = "material", length = 100)
    @Nationalized
    @Builder.Default
    private List<String> supportedMaterials = new ArrayList<>();

    @NotNull
    @Positive
    @Column(name = "minimum_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal minimumPrice;

    @Column(name = "max_print_size", length = 100)
    private String maxPrintSize;

    @NotNull
    @Positive
    @Column(name = "estimated_production_days", nullable = false)
    private Integer estimatedProductionDays;
}
