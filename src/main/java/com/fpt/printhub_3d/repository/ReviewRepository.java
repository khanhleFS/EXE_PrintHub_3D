package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    List<Review> findByProductId(UUID productId);

    long countByProductId(UUID productId);

    long countByProductIdAndRating(UUID productId, Short rating);

    @Query("SELECT AVG(CAST(r.rating AS double)) FROM Review r WHERE r.product.id = :productId")
    Double calculateAverageRatingByProductId(@Param("productId") UUID productId);
}
