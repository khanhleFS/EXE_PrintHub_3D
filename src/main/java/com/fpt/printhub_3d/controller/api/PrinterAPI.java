package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.printer.PrinterRequestDTO;
import com.fpt.printhub_3d.dto.printer.PrinterResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/admin/printers")
@Tag(name = "Printer APIs", description = "APIs for factory 3D printer telemetry and management")
public interface PrinterAPI {

    @Operation(
            summary = "Get all printers",
            description = "Quáº£n trá»‹ viÃªn xem danh sÃ¡ch cÃ¡c mÃ¡y in 3D trong xÆ°á»Ÿng.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping
    ResponseEntity<ApiResponse<List<PrinterResponseDTO>>> getAllPrinters();

    @Operation(
            summary = "Add new printer",
            description = "ThÃªm mÃ¡y in 3D má»›i vÃ o xÆ°á»Ÿng.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping
    ResponseEntity<ApiResponse<PrinterResponseDTO>> createPrinter(
            @Valid @RequestBody PrinterRequestDTO request);

    @Operation(
            summary = "Update printer",
            description = "Cáº­p nháº­t thÃ´ng tin hoáº·c tráº¡ng thÃ¡i mÃ¡y in (IDLE, PRINTING, MAINTENANCE...).",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping("/{id}")
    ResponseEntity<ApiResponse<PrinterResponseDTO>> updatePrinter(
            @PathVariable UUID id,
            @Valid @RequestBody PrinterRequestDTO request);
}
