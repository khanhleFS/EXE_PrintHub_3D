package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.CustomOrderReviewAPI;
import com.fpt.printhub_3d.dto.review.CustomOrderReviewRequestDTO;
import com.fpt.printhub_3d.dto.review.CustomOrderReviewResponseDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.CustomOrderReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
public class CustomOrderReviewController implements CustomOrderReviewAPI {

    private final CustomOrderReviewService customOrderReviewService;

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CustomOrderReviewResponseDTO>> upsertCustomOrderReview(
            UUID id,
            CustomOrderReviewRequestDTO request
    ) {
        User reviewer = SecurityUtils.getCurrentUser();
        CustomOrderReviewResponseDTO result = customOrderReviewService.upsertCustomOrderReview(id, request, reviewer);
        return ResponseEntity.ok(ApiResponse.<CustomOrderReviewResponseDTO>builder()
                .code(200)
                .message("Lưu đánh giá dịch vụ in 3D thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CustomOrderReviewResponseDTO>> getCustomOrderReview(UUID id) {
        User currentUser = SecurityUtils.getCurrentUser();
        CustomOrderReviewResponseDTO result = customOrderReviewService.getCustomOrderReview(id, currentUser);
        return ResponseEntity.ok(ApiResponse.<CustomOrderReviewResponseDTO>builder()
                .code(200)
                .message("Lấy đánh giá dịch vụ in 3D thành công")
                .result(result)
                .build());
    }
}
