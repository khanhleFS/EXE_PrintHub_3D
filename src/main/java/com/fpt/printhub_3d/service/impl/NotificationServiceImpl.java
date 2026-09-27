package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.dto.notification.NotificationResponseDTO;
import com.fpt.printhub_3d.entity.Notification;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.NotificationRepository;
import com.fpt.printhub_3d.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponseDTO> getMyNotifications(User user) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .limit(100)
                .map(n -> NotificationResponseDTO.builder()
                        .id(n.getId())
                        .title(n.getTitle())
                        .message(n.getMessage())
                        .timestamp(n.getCreatedAt())
                        .read(Boolean.TRUE.equals(n.getIsRead()))
                        .type(n.getType() != null ? n.getType() : "ORDER")
                        .link("/orders")
                        .build())
                .toList();
    }

    @Override
    public void markAsRead(UUID id, User user) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông báo"));

        if (!notification.getUser().getId().equals(user.getId())) {
            throw new ApiException(CommonErrorCode.UNAUTHORIZED, "Bạn không có quyền thao tác trên thông báo này");
        }

        notification.setIsRead(true);
        notification.setUpdatedAt(Instant.now());
        notificationRepository.save(notification);
    }

    @Override
    public void markAllAsRead(User user) {
        notificationRepository.markAllAsReadByUserId(user.getId());
    }

    @Override
    public void sendNotification(User user, String title, String message, String type, String link) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type != null ? type : "ORDER");
        notification.setIsRead(false);
        notification.setCreatedAt(Instant.now());
        notification.setUpdatedAt(Instant.now());
        notificationRepository.save(notification);
    }
}
