package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.UserSubscriptionAPI;
import com.fpt.printhub_3d.dto.subscription.BenefitPreviewRequestDTO;
import com.fpt.printhub_3d.dto.subscription.BenefitPreviewResponseDTO;
import com.fpt.printhub_3d.dto.subscription.CreateSubscriptionPurchaseRequestDTO;
import com.fpt.printhub_3d.dto.subscription.GiftSubscriptionRequestDTO;
import com.fpt.printhub_3d.dto.subscription.RedeemSubscriptionRequestDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanResponseDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPurchaseResponseDTO;
import com.fpt.printhub_3d.dto.subscription.UserSubscriptionResponseDTO;
import com.fpt.printhub_3d.dto.subscription.UserSubscriptionSummaryDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import com.fpt.printhub_3d.service.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
public class UserSubscriptionController implements UserSubscriptionAPI {

    private final UserSubscriptionService userSubscriptionService;
    private final SubscriptionBenefitService subscriptionBenefitService;

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanResponseDTO>>> getAvailablePlans() {
        UUID userId = SecurityUtils.getCurrentUser().getId();

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
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserSubscriptionResponseDTO>> redeemSubscription(
            UUID planId, RedeemSubscriptionRequestDTO request) {
        UUID userId = SecurityUtils.getCurrentUser().getId();
        String idempotencyKey = request != null ? request.idempotencyKey() : null;

        UserSubscriptionResponseDTO response = userSubscriptionService.redeemSubscription(userId, planId, idempotencyKey);

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

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserSubscriptionSummaryDTO>> getMySubscriptionSummary() {
        UUID userId = SecurityUtils.getCurrentUser().getId();
        UserSubscriptionSummaryDTO summary =
                userSubscriptionService.getUserSubscriptionSummary(userId);

        return ResponseEntity.ok(
                ApiResponse.<UserSubscriptionSummaryDTO>builder()
                        .code(200)
                        .message("Lấy thông tin gói hội viên thành công.")
                        .result(summary)
                        .build()
        );
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SubscriptionPurchaseResponseDTO>> createPurchase(
            CreateSubscriptionPurchaseRequestDTO request) {
        UUID userId = SecurityUtils.getCurrentUser().getId();
        SubscriptionPurchaseResponseDTO response = userSubscriptionService.createPurchase(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<SubscriptionPurchaseResponseDTO>builder()
                        .code(201)
                        .message("Tạo đơn mua gói hội viên thành công.")
                        .result(response)
                        .build()
        );
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SubscriptionPurchaseResponseDTO>> getPurchaseById(UUID id) {
        UUID userId = SecurityUtils.getCurrentUser().getId();
        SubscriptionPurchaseResponseDTO response = userSubscriptionService.getPurchaseById(id, userId);

        return ResponseEntity.ok(
                ApiResponse.<SubscriptionPurchaseResponseDTO>builder()
                        .code(200)
                        .message("Lấy chi tiết đơn mua gói thành công.")
                        .result(response)
                        .build()
        );
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SubscriptionPurchaseResponseDTO>>> getMyPurchases() {
        UUID userId = SecurityUtils.getCurrentUser().getId();
        List<SubscriptionPurchaseResponseDTO> response = userSubscriptionService.getMyPurchases(userId);

        return ResponseEntity.ok(
                ApiResponse.<List<SubscriptionPurchaseResponseDTO>>builder()
                        .code(200)
                        .message("Lấy lịch sử mua gói hội viên thành công.")
                        .result(response)
                        .build()
        );
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BenefitPreviewResponseDTO>> previewBenefits(BenefitPreviewRequestDTO request) {
        User currentUser = SecurityUtils.getCurrentUser();
        SubscriptionBenefitService.BenefitScope scope = SubscriptionBenefitService.BenefitScope.MARKETPLACE;
        if (request != null && request.getScope() != null && !request.getScope().isBlank()) {
            try {
                scope = SubscriptionBenefitService.BenefitScope.valueOf(
                        request.getScope().trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                throw new ApiException(CommonErrorCode.BAD_REQUEST,
                        "Phạm vi xem trước ưu đãi không hợp lệ: " + request.getScope());
            }
        }
        java.math.BigDecimal itemsSubtotal = request != null && request.getItemsSubtotal() != null
                ? request.getItemsSubtotal()
                : java.math.BigDecimal.ZERO;
        java.math.BigDecimal baseShippingFee = request != null && request.getBaseShippingFee() != null
                ? request.getBaseShippingFee()
                : java.math.BigDecimal.ZERO;
        java.math.BigDecimal customSubtotal = request != null ? request.getCustomSubtotal() : null;
        java.math.BigDecimal marketplaceSubtotal = request != null ? request.getMarketplaceSubtotal() : null;

        BenefitPreviewResponseDTO preview = subscriptionBenefitService.previewBenefits(
                currentUser, scope, itemsSubtotal, baseShippingFee, customSubtotal, marketplaceSubtotal);

        return ResponseEntity.ok(
                ApiResponse.<BenefitPreviewResponseDTO>builder()
                        .code(200)
                        .message("Tính trước quyền lợi gói hội viên thành công.")
                        .result(preview)
                        .build()
        );
    }
}
