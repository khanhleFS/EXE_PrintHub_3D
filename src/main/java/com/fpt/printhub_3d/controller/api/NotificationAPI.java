package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.notification.NotificationResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/notifications")
@Tag(name = "Notification APIs", description = "APIs for user notifications")
public interface NotificationAPI {

    @Operation(
            summary = "Get user notifications",
            description = "Lấy danh sách thông báo của người dùng hiện tại.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping
    ResponseEntity<ApiResponse<List<NotificationResponseDTO>>> getNotifications();

    @Operation(
            summary = "Mark notification as read",
            description = "Đánh dấu một thông báo đã đọc.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping("/{id}/read")
    ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable UUID id);

    @Operation(
            summary = "Mark all notifications as read",
            description = "Đánh dấu tất cả thông báo của người dùng đã đọc.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping("/read-all")
    ResponseEntity<ApiResponse<Void>> markAllAsRead();
}
