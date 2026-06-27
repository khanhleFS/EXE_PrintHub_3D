package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, UUID> {
    boolean existsByOrderId(UUID orderId);
}
