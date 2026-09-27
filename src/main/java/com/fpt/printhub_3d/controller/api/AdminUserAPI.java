package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.authen.AdminUserResponseDTO;
import com.fpt.printhub_3d.dto.authen.UpdateUserLockRequestDTO;
import com.fpt.printhub_3d.dto.authen.UpdateUserRoleRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/admin/users")
@Tag(name = "Admin User Management APIs", description = "APIs for administrator to manage system users")
public interface AdminUserAPI {

    @Operation(
            summary = "Get all users (Admin)",
            description = "Quản trị viên xem danh sách toàn bộ người dùng trong hệ thống.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping
    ResponseEntity<ApiResponse<List<AdminUserResponseDTO>>> getAllUsers();

    @Operation(
            summary = "Update user role",
            description = "Phân quyền vai trò mới cho người dùng (USER, ADMIN).",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping("/{id}/role")
    ResponseEntity<ApiResponse<Void>> updateUserRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRoleRequestDTO request);

    @Operation(
            summary = "Lock or unlock user",
            description = "Khóa hoặc mở khóa quyền truy cập của người dùng.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping("/{id}/lock")
    ResponseEntity<ApiResponse<Void>> updateUserLock(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserLockRequestDTO request);
}
