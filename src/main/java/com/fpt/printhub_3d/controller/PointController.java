package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.PointAPI;
import com.fpt.printhub_3d.dto.point.PointTransactionResponseDTO;
import com.fpt.printhub_3d.dto.point.PointWalletResponseDTO;
import com.fpt.printhub_3d.service.PointWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
public class PointController implements PointAPI {

    private final PointWalletService pointWalletService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PointWalletResponseDTO>> getMyWallet() {
        UUID userId = SecurityUtils.getCurrentUser().getId();
        PointWalletResponseDTO response = pointWalletService.getMyWallet(userId);
        return ResponseEntity.ok(ApiResponse.<PointWalletResponseDTO>builder()
                .code(200)
                .message("Lấy thông tin ví điểm thành công.")
                .result(response)
                .build());
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<PointTransactionResponseDTO>>> getMyTransactions(Pageable pageable) {
        UUID userId = SecurityUtils.getCurrentUser().getId();
        Page<PointTransactionResponseDTO> response = pointWalletService.getMyTransactions(userId, pageable);
        return ResponseEntity.ok(ApiResponse.<Page<PointTransactionResponseDTO>>builder()
                .code(200)
                .message("Lấy lịch sử giao dịch điểm thành công.")
                .result(response)
                .build());
    }
}

