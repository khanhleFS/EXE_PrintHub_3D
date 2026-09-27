package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.warranty.WarrantyClaimRequestDTO;
import com.fpt.printhub_3d.dto.warranty.WarrantyClaimResponseDTO;
import com.fpt.printhub_3d.dto.warranty.WarrantyClaimStatusRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/warranty")
@Tag(name = "Warranty APIs", description = "APIs for warranty claims and processing")
public interface WarrantyAPI {

    @Operation(summary = "Get user warranty claims", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/user/{userId}")
    ResponseEntity<ApiResponse<List<WarrantyClaimResponseDTO>>> getClaimsByUser(@PathVariable String userId);

    @Operation(summary = "Get all warranty claims (Admin)", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/admin/claims")
    ResponseEntity<ApiResponse<List<WarrantyClaimResponseDTO>>> getAllClaims();

    @Operation(summary = "Create warranty claim", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PostMapping("/claim")
    ResponseEntity<ApiResponse<WarrantyClaimResponseDTO>> createClaim(
            @Valid @RequestBody WarrantyClaimRequestDTO request);

    @Operation(summary = "Update warranty claim status", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PutMapping("/admin/claim/{id}/status")
    ResponseEntity<ApiResponse<Void>> updateClaimStatus(
            @PathVariable UUID id,
            @Valid @RequestBody WarrantyClaimStatusRequestDTO request);
}
