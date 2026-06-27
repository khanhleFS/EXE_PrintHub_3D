package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByOrderId(UUID orderId);
}
