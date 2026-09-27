package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.dashboard.DashboardResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "Dashboard APIs", description = "APIs for dashboard telemetry and metrics")
public interface DashboardAPI {

    @Operation(summary = "Get user dashboard metrics", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/api/dashboard")
    ResponseEntity<ApiResponse<DashboardResponseDTO>> getDashboard();

    @Operation(summary = "Get system dashboard metrics (Admin)", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/api/admin/dashboard")
    ResponseEntity<ApiResponse<DashboardResponseDTO>> getAdminDashboard();
}
