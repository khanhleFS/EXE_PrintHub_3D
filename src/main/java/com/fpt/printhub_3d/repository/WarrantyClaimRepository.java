package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.WarrantyClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WarrantyClaimRepository extends JpaRepository<WarrantyClaim, UUID> {
    List<WarrantyClaim> findByUserId(UUID userId);
    List<WarrantyClaim> findByStatus(String status);
}
