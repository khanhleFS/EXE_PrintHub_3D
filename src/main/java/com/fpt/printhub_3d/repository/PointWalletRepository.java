package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.PointWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PointWalletRepository extends JpaRepository<PointWallet, UUID> {
}
