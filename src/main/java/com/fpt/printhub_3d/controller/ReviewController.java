package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.ReviewAPI;
import com.fpt.printhub_3d.dto.review.*;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
public class ReviewController implements ReviewAPI {

    private final ReviewService reviewService;

    @Override
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ProductReviewResponseDTO>> createOrUpdateProductReview(
            ProductReviewCreateRequestDTO request) {
        User reviewer = SecurityUtils.getCurrentUser();
        ProductReviewResponseDTO response = reviewService.createOrUpdateProductReview(request, reviewer);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ProductReviewResponseDTO>builder()
                        .code(201)
                        .message("Gửi đánh giá chất lượng sản phẩm thành công")
                        .result(response)
                        .build());
    }

    @Override
    public ResponseEntity<ApiResponse<Page<ProductReviewResponseDTO>>> getProductReviews(
            UUID productId, Pageable pageable) {
        Page<ProductReviewResponseDTO> page = reviewService.getProductReviews(productId, pageable);
        return ResponseEntity.ok(ApiResponse.<Page<ProductReviewResponseDTO>>builder()
                .code(200)
                .message("Lấy danh sách đánh giá sản phẩm thành công")
                .result(page)
                .build());
    }

    @Override
    public ResponseEntity<ApiResponse<ProductReviewSummaryDTO>> getProductReviewSummary(
            UUID productId) {
        ProductReviewSummaryDTO summary = reviewService.getProductReviewSummary(productId);
        return ResponseEntity.ok(ApiResponse.<ProductReviewSummaryDTO>builder()
                .code(200)
                .message("Lấy thông tin tóm tắt đánh giá thành công")
                .result(summary)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ReviewResponseDTO>> createOrderReview(
            ReviewCreateRequestDTO request) {
        User reviewer = SecurityUtils.getCurrentUser();
        ReviewResponseDTO response = reviewService.createReview(request, reviewer);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ReviewResponseDTO>builder()
                        .code(201)
                        .message("Tạo đánh giá đơn hàng thành công")
                        .result(response)
                        .build());
    }
}
