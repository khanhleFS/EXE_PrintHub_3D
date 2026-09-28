package com.fpt.printhub_3d.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Thông báo hệ thống cho người dùng")
public record NotificationResponseDTO(
        UUID id,
        String title,
        String message,
        Instant timestamp,
        boolean read,
        String type,
        String link
) {}
