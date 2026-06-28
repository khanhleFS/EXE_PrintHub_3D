package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Enumeration.SubscriptionType;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.entity.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {
    
    @Query("SELECT us FROM UserSubscription us WHERE us.user = :user " +
           "AND us.plan.type = :type AND us.isActive = true " +
           "AND us.endDate > CURRENT_TIMESTAMP ORDER BY us.endDate DESC")
    Optional<UserSubscription> findActiveSubscriptionByUserAndType(
            @Param("user") User user, 
            @Param("type") SubscriptionType type);
}
