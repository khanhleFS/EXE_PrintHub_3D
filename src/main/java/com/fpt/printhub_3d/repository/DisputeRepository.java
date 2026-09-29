package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Dispute e where e.id = :id")
    java.util.Optional<Dispute> findLockedById(@org.springframework.data.repository.query.Param("id") java.util.UUID id);

    boolean existsByOrderId(UUID orderId);

    long countByStatus(String status);

    long countByStatusIn(java.util.Collection<String> statuses);
}
