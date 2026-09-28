package com.fpt.printhub_3d.dto.warranty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
@Schema(description = "Cập nhật trạng thái bảo hành")
public record WarrantyClaimStatusRequestDTO(
        @NotBlank(message = "Trạng thái không được để trống")
        @Schema(description = "Trạng thái (APPROVED, REJECTED, REPLACED)", example = "APPROVED")
        String status
) {}
