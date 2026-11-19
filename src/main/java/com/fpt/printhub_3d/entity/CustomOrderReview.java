package com.fpt.printhub_3d.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Đánh giá dịch vụ in 3D theo từng dòng thiết kế CustomOrder.
 * Tách riêng khỏi bảng reviews của sản phẩm catalog để tuyệt đối không làm lệch
 * điểm sao trung bình, số lượt đánh giá và giới hạn 10 đánh giá công khai của catalog (DG-02, DG-09).
 */
@Getter
@Setter
@Entity
@Table(
        name = "custom_order_reviews",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_custom_order_reviews_custom_order_id", columnNames = "custom_order_id")
        }
)
public class CustomOrderReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "custom_order_id", nullable = false, unique = true)
    private CustomOrder customOrder;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @Column(name = "rating", nullable = false)
    private Short rating;

    @Nationalized
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @ElementCollection
    @CollectionTable(name = "custom_order_review_images", joinColumns = @JoinColumn(name = "custom_order_review_id"))
    @Column(name = "image_url", length = 500)
    private List<String> imageUrls = new ArrayList<>();

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
