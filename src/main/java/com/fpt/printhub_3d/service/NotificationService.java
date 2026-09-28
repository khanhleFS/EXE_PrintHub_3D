package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.notification.NotificationResponseDTO;
import com.fpt.printhub_3d.entity.User;

import java.util.List;
import java.util.UUID;

public interface NotificationService {
    List<NotificationResponseDTO> getMyNotifications(User user);
    void markAsRead(UUID id, User user);
    void markAllAsRead(User user);
    void sendNotification(User user, String title, String message, String type, String link);
}
