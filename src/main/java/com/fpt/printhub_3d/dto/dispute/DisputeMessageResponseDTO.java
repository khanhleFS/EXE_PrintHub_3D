package com.fpt.printhub_3d.dto.dispute;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Builder
@Schema(description = "Tin nhắn trao đổi khiếu nại")
public record DisputeMessageResponseDTO(
        Long id,
        String author,
        String content,
        Instant createdAt
) {}
