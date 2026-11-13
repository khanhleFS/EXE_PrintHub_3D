package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Review;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByOrderId(UUID orderId);

    boolean existsByOrderItemId(Long orderItemId);

    Optional<Review> findByOrderItemId(Long orderItemId);

    List<Review> findByOrderId(UUID orderId);

    List<Review> findByOrderIdIn(Collection<UUID> orderIds);

    Page<Review> findByProductId(UUID productId, Pageable pageable);

    @EntityGraph(attributePaths = {"reviewer", "product", "orderItem"})
    Page<Review> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"reviewer", "product", "orderItem"})
    Page<Review> findByRating(Short rating, Pageable pageable);

    @EntityGraph(attributePaths = {"reviewer", "product", "orderItem"})
    Page<Review> findByIsPublicTrue(Pageable pageable);

    @EntityGraph(attributePaths = {"reviewer", "product", "orderItem"})
    @Query("SELECT r FROM Review r WHERE (r.isPublic = false OR r.isPublic IS NULL)")
    Page<Review> findHiddenReviews(Pageable pageable);

    @EntityGraph(attributePaths = {"reviewer", "product", "orderItem"})
    Page<Review> findByRatingAndIsPublicTrue(Short rating, Pageable pageable);

    @EntityGraph(attributePaths = {"reviewer", "product", "orderItem"})
    @Query("SELECT r FROM Review r WHERE r.rating = :rating AND (r.isPublic = false OR r.isPublic IS NULL)")
    Page<Review> findHiddenReviewsByRating(@Param("rating") Short rating, Pageable pageable);

    long countByIsPublicTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Review r WHERE r.id = :id")
    Optional<Review> findByIdLocked(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Review r WHERE r.isPublic = true")
    List<Review> findAllPublicLocked();

    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.reviewer
            LEFT JOIN FETCH r.product
            WHERE r.isPublic = true
              AND UPPER(r.order.status) = 'COMPLETED'
            ORDER BY COALESCE(r.publicUpdatedAt, r.createdAt) DESC, r.id DESC
            """)
    List<Review> findFeaturedReviews(Pageable pageable);

    List<Review> findByProductId(UUID productId);

    long countByProductId(UUID productId);

    long countByProductIdAndRating(UUID productId, Short rating);

    @Query("SELECT AVG(CAST(r.rating AS double)) FROM Review r WHERE r.product.id = :productId")
    Double calculateAverageRatingByProductId(@Param("productId") UUID productId);
}
