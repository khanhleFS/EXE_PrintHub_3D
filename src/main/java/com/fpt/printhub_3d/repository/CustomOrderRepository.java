package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.CustomOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomOrderRepository extends JpaRepository<CustomOrder, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CustomOrder e where e.id = :id")
    Optional<CustomOrder> findLockedById(@Param("id") UUID id);

    long countByAttachmentUrl(String attachmentUrl);

    List<CustomOrder> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    List<CustomOrder> findAllByOrderByCreatedAtDesc();
}
