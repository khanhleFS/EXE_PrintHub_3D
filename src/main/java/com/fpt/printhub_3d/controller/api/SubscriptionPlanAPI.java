package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanRequestDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequestMapping("/api/admin/subscriptions")
@Tag(name = "Subscription Admin APIs", description = "APIs for managing Customer subscription plans")
public interface SubscriptionPlanAPI {

    @Operation(summary = "Get all subscription plans for Admin", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping
    ResponseEntity<ApiResponse<java.util.List<SubscriptionPlanResponseDTO>>> getAllPlansForAdmin();

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
}
