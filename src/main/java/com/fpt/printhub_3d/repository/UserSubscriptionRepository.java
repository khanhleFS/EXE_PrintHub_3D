package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Enumeration.SubscriptionType;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.entity.UserSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
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

    @Query("SELECT us FROM UserSubscription us WHERE us.user = :user " +
           "AND us.isActive = true " +
           "AND us.endDate > CURRENT_TIMESTAMP ORDER BY us.endDate DESC")
    List<UserSubscription> findActiveSubscriptionsByUser(@Param("user") User user);

    @Query("SELECT CASE WHEN COUNT(us) > 0 THEN true ELSE false END FROM UserSubscription us " +
           "WHERE us.user = :user AND us.endDate <= CURRENT_TIMESTAMP")
    boolean hasExpiredSubscription(@Param("user") User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT us FROM UserSubscription us WHERE us.user = :user " +
           "AND us.isActive = true " +
           "AND us.endDate > CURRENT_TIMESTAMP ORDER BY us.id")
    List<UserSubscription> findLockedActiveSubscriptionsByUser(@Param("user") User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT us FROM UserSubscription us WHERE us.id IN :ids ORDER BY us.id")
    List<UserSubscription> findLockedByIds(@Param("ids") Collection<UUID> ids);
}
