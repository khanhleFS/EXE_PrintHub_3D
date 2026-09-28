package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.updatedAt = CURRENT_TIMESTAMP WHERE n.user.id = :userId")
    void markAllAsReadByUserId(@Param("userId") UUID userId);
}
