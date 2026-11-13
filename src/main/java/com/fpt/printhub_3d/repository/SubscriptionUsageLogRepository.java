package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.SubscriptionUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionUsageLogRepository extends JpaRepository<SubscriptionUsageLog, UUID> {

    List<SubscriptionUsageLog> findByTargetTypeAndTargetId(String targetType, UUID targetId);

    List<SubscriptionUsageLog> findByTargetTypeAndTargetIdAndStatus(String targetType, UUID targetId, String status);

    List<SubscriptionUsageLog> findByTargetTypeAndTargetIdAndStatusIn(
            String targetType,
            UUID targetId,
            Collection<String> statuses
    );

    long countByUserSubscriptionIdAndBenefitTypeAndStatusIn(
            UUID userSubscriptionId,
            String benefitType,
            Collection<String> statuses
    );
}

