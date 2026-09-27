package com.fpt.printhub_3d.dto.dispute;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
@Schema(description = "Nội dung tin nhắn trong phiên giải quyết khiếu nại")
public record DisputeMessageRequestDTO(
        @NotBlank(message = "Nội dung tin nhắn không được để trống")
        @Size(max = 4000, message = "Nội dung tối đa 4000 ký tự")
        @Schema(description = "Nội dung trao đổi")
        String content
) {}
