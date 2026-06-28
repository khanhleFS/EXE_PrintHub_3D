package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Enumeration.SubscriptionType;
import com.fpt.printhub_3d.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {
    boolean existsByNameIgnoreCaseAndType(String name, SubscriptionType type);

    Optional<SubscriptionPlan> findByNameIgnoreCaseAndType(String name, SubscriptionType type);

    List<SubscriptionPlan> findByTypeAndIsActiveTrue(SubscriptionType type);
}
