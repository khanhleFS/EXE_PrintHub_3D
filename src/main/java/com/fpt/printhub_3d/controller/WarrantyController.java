package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.WarrantyAPI;
import com.fpt.printhub_3d.dto.warranty.WarrantyClaimRequestDTO;
import com.fpt.printhub_3d.dto.warranty.WarrantyClaimResponseDTO;
import com.fpt.printhub_3d.dto.warranty.WarrantyClaimStatusRequestDTO;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.WarrantyService;
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
public class WarrantyController implements WarrantyAPI {

    private final WarrantyService warrantyService;

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<WarrantyClaimResponseDTO>>> getClaimsByUser(String userId) {
        User user = SecurityUtils.getCurrentUser();
        List<WarrantyClaimResponseDTO> list = warrantyService.getClaimsByUser(user);
        return ResponseEntity.ok(ApiResponse.<List<WarrantyClaimResponseDTO>>builder()
                .code(200)
                .message("Lấy danh sách yêu cầu bảo hành thành công")
                .result(list)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<WarrantyClaimResponseDTO>>> getAllClaims() {
        List<WarrantyClaimResponseDTO> list = warrantyService.getAllClaims();
        return ResponseEntity.ok(ApiResponse.<List<WarrantyClaimResponseDTO>>builder()
                .code(200)
                .message("Lấy toàn bộ yêu cầu bảo hành thành công")
                .result(list)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<WarrantyClaimResponseDTO>> createClaim(WarrantyClaimRequestDTO request) {
        User user = SecurityUtils.getCurrentUser();
        WarrantyClaimResponseDTO result = warrantyService.createClaim(user, request);
        return ResponseEntity.ok(ApiResponse.<WarrantyClaimResponseDTO>builder()
                .code(200)
                .message("Tạo yêu cầu bảo hành thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateClaimStatus(UUID id, WarrantyClaimStatusRequestDTO request) {
        User admin = SecurityUtils.getCurrentUser();
        warrantyService.updateClaimStatus(id, request, admin);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Cập nhật trạng thái bảo hành thành công")
                .build());
    }
}
