package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanRequestDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@RequestMapping("/api/admin/subscriptions")
@Tag(name = "Subscription Admin APIs", description = "APIs for managing Customer and Maker subscription plans")
public interface SubscriptionPlanAPI {

    @Operation(summary = "Create Customer subscription plan", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PostMapping("/customer")
    ResponseEntity<ApiResponse<SubscriptionPlanResponseDTO>> createCustomerPlan(
            @Valid @RequestBody SubscriptionPlanRequestDTO request);

    @Operation(summary = "Update Customer subscription plan", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PutMapping("/customer/{id}")
    ResponseEntity<ApiResponse<SubscriptionPlanResponseDTO>> updateCustomerPlan(
            @PathVariable UUID id,
            @Valid @RequestBody SubscriptionPlanRequestDTO request);

    @Operation(summary = "Delete Customer subscription plan", security = @SecurityRequirement(name = "Bearer Authentication"))
    @DeleteMapping("/customer/{id}")
    ResponseEntity<ApiResponse<Void>> deleteCustomerPlan(@PathVariable UUID id);

    @Operation(summary = "Create Maker marketing subscription plan", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PostMapping("/maker")
    ResponseEntity<ApiResponse<SubscriptionPlanResponseDTO>> createMakerPlan(
            @Valid @RequestBody SubscriptionPlanRequestDTO request);

    @Operation(summary = "Update Maker marketing subscription plan", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PutMapping("/maker/{id}")
    ResponseEntity<ApiResponse<SubscriptionPlanResponseDTO>> updateMakerPlan(
            @PathVariable UUID id,
            @Valid @RequestBody SubscriptionPlanRequestDTO request);

    @Operation(summary = "Delete Maker marketing subscription plan", security = @SecurityRequirement(name = "Bearer Authentication"))
    @DeleteMapping("/maker/{id}")
    ResponseEntity<ApiResponse<Void>> deleteMakerPlan(@PathVariable UUID id);
}
