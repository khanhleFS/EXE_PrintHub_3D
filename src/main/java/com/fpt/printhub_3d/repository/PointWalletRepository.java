package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.PointWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PointWalletRepository extends JpaRepository<PointWallet, UUID> {
    Optional<PointWallet> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pw FROM PointWallet pw WHERE pw.userId = :userId")
    Optional<PointWallet> findByUserIdForUpdate(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM PointWallet pw WHERE pw.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
