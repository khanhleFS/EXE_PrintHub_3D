package com.fpt.printhub_3d.dto.printer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Thông tin máy in 3D trong xưởng")
public record PrinterResponseDTO(
        UUID id,
        String name,
        String type,
        String status,
        String note
) {}
