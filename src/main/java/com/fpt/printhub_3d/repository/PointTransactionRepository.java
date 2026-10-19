package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    boolean existsByPointWalletUserIdAndDescription(UUID userId, String description);
}
