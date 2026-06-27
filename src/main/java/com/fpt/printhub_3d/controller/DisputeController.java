package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.security.CustomUserDetail;
import com.fpt.printhub_3d.controller.api.DisputeAPI;
import com.fpt.printhub_3d.dto.dispute.DisputeCreateRequestDTO;
import com.fpt.printhub_3d.dto.dispute.DisputeResponseDTO;
import com.fpt.printhub_3d.dto.dispute.DisputeResolutionRequestDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.DisputeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DisputeController implements DisputeAPI {

    private final DisputeService disputeService;

    @Override
    @PreAuthorize("hasAnyRole('USER', 'MAKER')")
    public ResponseEntity<ApiResponse<DisputeResponseDTO>> createDispute(DisputeCreateRequestDTO request) {
        CustomUserDetail userDetail = (CustomUserDetail) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        User filedBy = userDetail.getUser();

        DisputeResponseDTO response = disputeService.createDispute(request, filedBy);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<DisputeResponseDTO>builder()
                        .code(201)
                        .message("Khởi tạo hồ sơ khiếu nại thành công")
                        .result(response)
                        .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DisputeResponseDTO>> resolveDispute(UUID id, DisputeResolutionRequestDTO request) {
        DisputeResponseDTO response = disputeService.resolveDispute(id, request);
        return ResponseEntity.ok(ApiResponse.<DisputeResponseDTO>builder()
                .code(200)
                .message("Xử lý phán quyết khiếu nại thành công")
                .result(response)
                .build());
    }
}
