package com.fpt.printhub_3d.dto.vault;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin tệp thiết kế trong kho lưu trữ File Vault")
public record FileAssetResponseDTO(
        @Schema(description = "ID tệp lưu trữ")
        UUID id,

        @Schema(description = "Tên tệp gốc", example = "custom_part.stl")
        String fileName,

        @Schema(description = "Kích thước tệp tính theo bytes", example = "1048576")
        Long sizeBytes,

        @Schema(description = "Thời gian tải lên")
        Instant createdAt
) {}
