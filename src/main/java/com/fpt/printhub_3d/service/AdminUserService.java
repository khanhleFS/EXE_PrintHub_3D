package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.authen.AdminUserResponseDTO;
import com.fpt.printhub_3d.dto.authen.UpdateUserLockRequestDTO;
import com.fpt.printhub_3d.dto.authen.UpdateUserRoleRequestDTO;
import com.fpt.printhub_3d.entity.User;

import java.util.List;
import java.util.UUID;

public interface AdminUserService {
    List<AdminUserResponseDTO> getAllUsers();
    void updateUserRole(UUID userId, UpdateUserRoleRequestDTO request, User currentUser);
    void updateUserLock(UUID userId, UpdateUserLockRequestDTO request, User currentUser);
}
