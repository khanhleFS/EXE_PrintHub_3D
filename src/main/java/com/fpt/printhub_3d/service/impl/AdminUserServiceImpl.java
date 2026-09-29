package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.dto.authen.AdminUserResponseDTO;
import com.fpt.printhub_3d.dto.authen.UpdateUserLockRequestDTO;
import com.fpt.printhub_3d.dto.authen.UpdateUserRoleRequestDTO;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.UserRepository;
import com.fpt.printhub_3d.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AdminUserResponseDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        User::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .map(u -> AdminUserResponseDTO.builder()
                        .id(u.getId())
                        .name(u.getFullName())
                        .email(u.getEmail())
                        .role(u.getRole() != null ? u.getRole().name() : "USER")
                        .isLocked(!Boolean.TRUE.equals(u.getIsActive()))
                        .createdAt(u.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    public void updateUserRole(UUID userId, UpdateUserRoleRequestDTO request, User currentUser) {
        if (userId.equals(currentUser.getId())) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Không thể tự thay đổi vai trò tài khoản đang đăng nhập");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng"));

        String roleStr = request.role().toUpperCase();
        if (!Set.of("USER", "ADMIN").contains(roleStr)) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Vai trò không hợp lệ: " + roleStr);
        }

        user.setRole(UserRole.valueOf(roleStr));
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userRepository.save(user);
    }

    @Override
    public void updateUserLock(UUID userId, UpdateUserLockRequestDTO request, User currentUser) {
        if (userId.equals(currentUser.getId())) {
            throw new ApiException(CommonErrorCode.FORBIDDEN, "Không thể tự khóa tài khoản đang đăng nhập");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng"));

        user.setIsActive(!request.isLocked());
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userRepository.save(user);
    }
}
