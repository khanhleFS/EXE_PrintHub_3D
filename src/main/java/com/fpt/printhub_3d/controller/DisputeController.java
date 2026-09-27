package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.DisputeAPI;
import com.fpt.printhub_3d.dto.dispute.*;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.DisputeService;
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
public class DisputeController implements DisputeAPI {

    private final DisputeService disputeService;

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<DisputeDetailDTO>>> getMyDisputes() {
        User user = SecurityUtils.getCurrentUser();
        List<DisputeDetailDTO> list = disputeService.getMyDisputes(user);
        return ResponseEntity.ok(ApiResponse.<List<DisputeDetailDTO>>builder()
                .code(200)
                .message("Lấy danh sách khiếu nại thành công")
                .result(list)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<DisputeDetailDTO>>> getAllDisputes() {
        List<DisputeDetailDTO> list = disputeService.getAllDisputes();
        return ResponseEntity.ok(ApiResponse.<List<DisputeDetailDTO>>builder()
                .code(200)
                .message("Lấy toàn bộ danh sách khiếu nại thành công")
                .result(list)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<DisputeMessageResponseDTO>>> getMessages(UUID id) {
        User user = SecurityUtils.getCurrentUser();
        List<DisputeMessageResponseDTO> list = disputeService.getMessages(id, user);
        return ResponseEntity.ok(ApiResponse.<List<DisputeMessageResponseDTO>>builder()
                .code(200)
                .message("Lấy tin nhắn khiếu nại thành công")
                .result(list)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> sendMessage(UUID id, DisputeMessageRequestDTO request) {
        User user = SecurityUtils.getCurrentUser();
        disputeService.sendMessage(id, request, user);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Gửi tin nhắn thành công")
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DisputeResponseDTO>> createDispute(DisputeCreateRequestDTO request) {
        User filedBy = SecurityUtils.getCurrentUser();
        DisputeResponseDTO response = disputeService.createDispute(request, filedBy);
        return ResponseEntity.ok(ApiResponse.<DisputeResponseDTO>builder()
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
                .message("Phán quyết hồ sơ tranh chấp thành công")
                .result(response)
                .build());
    }
}
