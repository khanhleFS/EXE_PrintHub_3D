package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.security.CustomUserDetail;
import com.fpt.printhub_3d.controller.api.UserSubscriptionAPI;
import com.fpt.printhub_3d.dto.subscription.GiftSubscriptionRequestDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanResponseDTO;
import com.fpt.printhub_3d.dto.subscription.UserSubscriptionResponseDTO;
import com.fpt.printhub_3d.service.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UserSubscriptionController implements UserSubscriptionAPI {

    private final UserSubscriptionService userSubscriptionService;

    @Override
    @PreAuthorize("hasAnyRole('USER', 'MAKER')")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanResponseDTO>>> getAvailablePlans() {
        CustomUserDetail userDetail = (CustomUserDetail) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID userId = userDetail.getUser().getId();

        List<SubscriptionPlanResponseDTO> response = userSubscriptionService.getAvailablePlansForUser(userId);

        return ResponseEntity.ok(
                ApiResponse.<List<SubscriptionPlanResponseDTO>>builder()
                        .code(200)
                        .message("Tải danh sách gói thành công.")
                        .result(response)
                        .build()
        );
    }

    @Override
    @PreAuthorize("hasAnyRole('USER', 'MAKER')")
    public ResponseEntity<ApiResponse<UserSubscriptionResponseDTO>> redeemSubscription(UUID planId) {
        CustomUserDetail userDetail = (CustomUserDetail) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID userId = userDetail.getUser().getId();
        
        UserSubscriptionResponseDTO response = userSubscriptionService.redeemSubscription(userId, planId);
        
        return ResponseEntity.ok(
                ApiResponse.<UserSubscriptionResponseDTO>builder()
                        .code(200)
                        .message("Đổi điểm nhận gói thành công.")
                        .result(response)
                        .build()
        );
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserSubscriptionResponseDTO>> giftSubscription(GiftSubscriptionRequestDTO request) {
        UserSubscriptionResponseDTO response = userSubscriptionService.giftSubscription(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<UserSubscriptionResponseDTO>builder()
                        .code(201)
                        .message("Phát gói dịch vụ cho người dùng thành công.")
                        .result(response)
                        .build()
        );
    }
}
