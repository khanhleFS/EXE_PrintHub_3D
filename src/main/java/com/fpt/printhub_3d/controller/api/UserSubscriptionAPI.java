package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.subscription.GiftSubscriptionRequestDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanResponseDTO;
import com.fpt.printhub_3d.dto.subscription.UserSubscriptionResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@Tag(name = "User Subscription APIs", description = "Các API đăng ký gói dịch vụ hội viên cho User và chức năng phát gói của Admin")
public interface UserSubscriptionAPI {

    @Operation(
            summary = "Get active subscription plans for current user role",
            description = "Lấy danh sách các gói dịch vụ đang hoạt động phù hợp với vai trò của người dùng (USER -> CUSTOMER_VIP, MAKER -> MAKER_MARKETING).",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/api/subscriptions/plans")
    ResponseEntity<ApiResponse<List<SubscriptionPlanResponseDTO>>> getAvailablePlans();

    @Operation(
            summary = "Redeem subscription plan with reward points",
            description = "Người mua hoặc người bán dùng điểm thưởng tích lũy để đổi gói dịch vụ tương ứng.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/api/subscriptions/redeem/{planId}")
    ResponseEntity<ApiResponse<UserSubscriptionResponseDTO>> redeemSubscription(
            @PathVariable("planId") UUID planId);

    @Operation(
            summary = "Admin gifts subscription plan to a user",
            description = "Admin phát gói quà tặng hoặc đền bù dịch vụ cho một tài khoản bất kỳ.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/api/admin/subscriptions/gift")
    ResponseEntity<ApiResponse<UserSubscriptionResponseDTO>> giftSubscription(
            @Valid @RequestBody GiftSubscriptionRequestDTO request);
}
