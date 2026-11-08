package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.review.ProductReviewResponseDTO;
import com.fpt.printhub_3d.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Get customer reviews (Admin)",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<ProductReviewResponseDTO>> getAdminReviews(
            @RequestParam(name = "rating", required = false) @Min(1) @Max(5) Short rating,
            @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.<Page<ProductReviewResponseDTO>>builder()
                .code(200)
                .message("Lấy danh sách đánh giá khách hàng thành công")
                .result(reviewService.getAdminReviews(rating, pageable))
                .build();
    }
}
