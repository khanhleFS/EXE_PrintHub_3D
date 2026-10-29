package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.PointTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    List<PointTransaction> findByPointWallet_UserIdOrderByCreatedAtDesc(UUID userId);
    Page<PointTransaction> findByPointWallet_UserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
