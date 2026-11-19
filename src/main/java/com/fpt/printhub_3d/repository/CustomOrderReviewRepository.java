package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.CustomOrderReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomOrderReviewRepository extends JpaRepository<CustomOrderReview, Long> {
    Optional<CustomOrderReview> findByCustomOrderId(UUID customOrderId);
    List<CustomOrderReview> findByCustomOrderIdIn(Collection<UUID> customOrderIds);
    boolean existsByCustomOrderId(UUID customOrderId);
}
