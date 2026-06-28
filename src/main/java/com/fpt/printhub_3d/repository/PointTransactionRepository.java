package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
}
