package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Enumeration.OrderRewardStatus;
import com.fpt.printhub_3d.entity.OrderRewardRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRewardRecordRepository extends JpaRepository<OrderRewardRecord, UUID> {
    Optional<OrderRewardRecord> findBySourceTypeAndSourceIdAndRewardType(String sourceType, UUID sourceId, String rewardType);
    List<OrderRewardRecord> findBySourceTypeAndSourceId(String sourceType, UUID sourceId);
    List<OrderRewardRecord> findByStatus(OrderRewardStatus status);
}
