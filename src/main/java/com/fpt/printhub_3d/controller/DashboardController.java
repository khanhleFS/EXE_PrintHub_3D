package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.DashboardAPI;
import com.fpt.printhub_3d.dto.dashboard.DashboardResponseDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
public class DashboardController implements DashboardAPI {

    private final DashboardService dashboardService;

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DashboardResponseDTO>> getDashboard() {
        User user = SecurityUtils.getCurrentUser();
        DashboardResponseDTO result = dashboardService.getDashboard(user, false);
        return ResponseEntity.ok(ApiResponse.<DashboardResponseDTO>builder()
                .code(200)
                .message("Lấy số liệu dashboard thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DashboardResponseDTO>> getAdminDashboard() {
        User user = SecurityUtils.getCurrentUser();
        DashboardResponseDTO result = dashboardService.getDashboard(user, true);
        return ResponseEntity.ok(ApiResponse.<DashboardResponseDTO>builder()
                .code(200)
                .message("Lấy toàn bộ số liệu dashboard thành công")
                .result(result)
                .build());
    }
}
