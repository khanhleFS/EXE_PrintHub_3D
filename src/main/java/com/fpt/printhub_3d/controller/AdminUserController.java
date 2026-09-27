package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.AdminUserAPI;
import com.fpt.printhub_3d.dto.authen.AdminUserResponseDTO;
import com.fpt.printhub_3d.dto.authen.UpdateUserLockRequestDTO;
import com.fpt.printhub_3d.dto.authen.UpdateUserRoleRequestDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdminUserController implements AdminUserAPI {

    private final AdminUserService adminUserService;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AdminUserResponseDTO>>> getAllUsers() {
        List<AdminUserResponseDTO> users = adminUserService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.<List<AdminUserResponseDTO>>builder()
                .code(200)
                .message("Lấy danh sách người dùng thành công")
                .result(users)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateUserRole(UUID id, UpdateUserRoleRequestDTO request) {
        User currentUser = SecurityUtils.getCurrentUser();
        adminUserService.updateUserRole(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Cập nhật vai trò người dùng thành công")
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateUserLock(UUID id, UpdateUserLockRequestDTO request) {
        User currentUser = SecurityUtils.getCurrentUser();
        adminUserService.updateUserLock(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Cập nhật trạng thái khóa tài khoản thành công")
                .build());
    }
}
