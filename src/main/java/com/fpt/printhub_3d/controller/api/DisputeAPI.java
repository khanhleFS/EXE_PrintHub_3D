package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.dispute.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Dispute APIs", description = "APIs for disputes, messages, and admin resolutions")
public interface DisputeAPI {

    @Operation(summary = "Get user disputes", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/api/disputes")
    ResponseEntity<ApiResponse<List<DisputeDetailDTO>>> getMyDisputes();

    @Operation(summary = "Get all disputes (Admin)", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/api/admin/disputes")
    ResponseEntity<ApiResponse<List<DisputeDetailDTO>>> getAllDisputes();

    @Operation(summary = "Get dispute messages", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/api/disputes/{id}/messages")
    ResponseEntity<ApiResponse<List<DisputeMessageResponseDTO>>> getMessages(@PathVariable UUID id);

    @Operation(summary = "Send message in dispute", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PostMapping("/api/disputes/{id}/messages")
    ResponseEntity<ApiResponse<Void>> sendMessage(
            @PathVariable UUID id,
            @Valid @RequestBody DisputeMessageRequestDTO request);

    @Operation(summary = "Create dispute", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PostMapping("/api/disputes")
    ResponseEntity<ApiResponse<DisputeResponseDTO>> createDispute(
            @Valid @RequestBody DisputeCreateRequestDTO request);

    @Operation(summary = "Resolve dispute (Admin)", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PutMapping("/api/admin/disputes/{id}/resolution")
    ResponseEntity<ApiResponse<DisputeResponseDTO>> resolveDispute(
            @PathVariable UUID id,
            @Valid @RequestBody DisputeResolutionRequestDTO request);
}
