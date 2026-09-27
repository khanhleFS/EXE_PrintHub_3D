package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.NotificationAPI;
import com.fpt.printhub_3d.dto.notification.NotificationResponseDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
public class NotificationController implements NotificationAPI {

    private final NotificationService notificationService;

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<NotificationResponseDTO>>> getNotifications() {
        User user = SecurityUtils.getCurrentUser();
        List<NotificationResponseDTO> notifications = notificationService.getMyNotifications(user);
        return ResponseEntity.ok(ApiResponse.<List<NotificationResponseDTO>>builder()
                .code(200)
                .message("Lấy danh sách thông báo thành công")
                .result(notifications)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> markAsRead(UUID id) {
        User user = SecurityUtils.getCurrentUser();
        notificationService.markAsRead(id, user);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Đánh dấu đã đọc thành công")
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        User user = SecurityUtils.getCurrentUser();
        notificationService.markAllAsRead(user);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Đánh dấu tất cả đã đọc thành công")
                .build());
    }
}
