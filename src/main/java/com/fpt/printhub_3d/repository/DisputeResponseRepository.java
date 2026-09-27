package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.DisputeRespons;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DisputeResponseRepository extends JpaRepository<DisputeRespons, Long> {
    List<DisputeRespons> findByDisputeIdOrderByCreatedAtAsc(UUID disputeId);
}
