package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.controller.api.SubscriptionPlanAPI;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanRequestDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanResponseDTO;
import com.fpt.printhub_3d.entity.Enumeration.SubscriptionType;
import com.fpt.printhub_3d.service.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SubscriptionPlanController implements SubscriptionPlanAPI {

    private final SubscriptionPlanService subscriptionPlanService;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponseDTO>> createCustomerPlan(SubscriptionPlanRequestDTO request) {
        SubscriptionPlanResponseDTO response = subscriptionPlanService.createPlan(SubscriptionType.CUSTOMER, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<SubscriptionPlanResponseDTO>builder()
                        .code(201)
                        .message("Tạo gói hội viên Customer thành công")
                        .result(response)
                        .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponseDTO>> updateCustomerPlan(UUID id, SubscriptionPlanRequestDTO request) {
        SubscriptionPlanResponseDTO response = subscriptionPlanService.updatePlan(SubscriptionType.CUSTOMER, id, request);
        return ResponseEntity.ok(ApiResponse.<SubscriptionPlanResponseDTO>builder()
                .code(200)
                .message("Cập nhật gói hội viên Customer thành công")
                .result(response)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCustomerPlan(UUID id) {
        subscriptionPlanService.deletePlan(SubscriptionType.CUSTOMER, id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Xóa gói hội viên Customer thành công")
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponseDTO>> createMakerPlan(SubscriptionPlanRequestDTO request) {
        SubscriptionPlanResponseDTO response = subscriptionPlanService.createPlan(SubscriptionType.MAKER, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<SubscriptionPlanResponseDTO>builder()
                        .code(201)
                        .message("Tạo gói Marketing Maker thành công")
                        .result(response)
                        .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponseDTO>> updateMakerPlan(UUID id, SubscriptionPlanRequestDTO request) {
        SubscriptionPlanResponseDTO response = subscriptionPlanService.updatePlan(SubscriptionType.MAKER, id, request);
        return ResponseEntity.ok(ApiResponse.<SubscriptionPlanResponseDTO>builder()
                .code(200)
                .message("Cập nhật gói Marketing Maker thành công")
                .result(response)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteMakerPlan(UUID id) {
        subscriptionPlanService.deletePlan(SubscriptionType.MAKER, id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Xóa gói Marketing Maker thành công")
                .build());
    }
}
