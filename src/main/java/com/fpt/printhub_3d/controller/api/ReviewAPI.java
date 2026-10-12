package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.review.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequestMapping("/api/reviews")
@Tag(name = "Review APIs", description = "APIs for customer reviews on 3D printed products")
public interface ReviewAPI {

    @Operation(
            summary = "Create or update product quality review",
            description = "Khách hàng gửi hoặc chỉnh sửa đánh giá chất lượng in cho sản phẩm trong đơn hàng đã hoàn tất (kèm ảnh thực tế).",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping
    ResponseEntity<ApiResponse<ProductReviewResponseDTO>> createOrUpdateProductReview(
            @Valid @RequestBody ProductReviewCreateRequestDTO request);

    @Operation(
            summary = "Get product reviews",
            description = "Lấy danh sách các đánh giá của sản phẩm có phân trang (Public)."
    )
    @GetMapping("/products/{productId}")
    ResponseEntity<ApiResponse<Page<ProductReviewResponseDTO>>> getProductReviews(
            @PathVariable UUID productId,
            Pageable pageable);

    @Operation(
            summary = "Get product review summary",
            description = "Lấy thống kê điểm sao trung bình và số lượng từng mức sao của sản phẩm (Public)."
    )
    @GetMapping("/products/{productId}/summary")
    ResponseEntity<ApiResponse<ProductReviewSummaryDTO>> getProductReviewSummary(
            @PathVariable UUID productId);

    @Operation(
            summary = "Create order review (legacy)",
            description = "Customer viết đánh giá cho toàn đơn hàng (tương thích ngược).",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/order")
    ResponseEntity<ApiResponse<ReviewResponseDTO>> createOrderReview(
            @Valid @RequestBody ReviewCreateRequestDTO request);
}
