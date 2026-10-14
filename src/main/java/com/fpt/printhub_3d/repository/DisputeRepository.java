package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Dispute;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Dispute e where e.id = :id")
    Optional<Dispute> findLockedById(@Param("id") UUID id);

    boolean existsByOrderId(UUID orderId);

    long countByStatus(String status);

    long countByStatusIn(java.util.Collection<String> statuses);
}
