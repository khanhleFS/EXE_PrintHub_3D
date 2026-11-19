package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.review.CustomOrderReviewRequestDTO;
import com.fpt.printhub_3d.dto.review.CustomOrderReviewResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Custom Order Review APIs", description = "Đánh giá dịch vụ in 3D theo từng dòng CustomOrder đã hoàn tất")
public interface CustomOrderReviewAPI {

    @Operation(
            summary = "Create or update review for a completed custom order line",
            description = "Tạo mới hoặc chỉnh sửa (trong hạn 7 ngày) đánh giá dịch vụ in 3D cho một dòng CustomOrder đã COMPLETED.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping("/api/custom-orders/{id}/review")
    ResponseEntity<ApiResponse<CustomOrderReviewResponseDTO>> upsertCustomOrderReview(
            @PathVariable UUID id,
            @Valid @RequestBody CustomOrderReviewRequestDTO request
    );

    @Operation(
            summary = "Get review of a custom order line",
            description = "Đọc lại đánh giá dịch vụ in 3D của dòng CustomOrder (chủ đơn hoặc Admin, vẫn đọc được sau 7 ngày).",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/api/custom-orders/{id}/review")
    ResponseEntity<ApiResponse<CustomOrderReviewResponseDTO>> getCustomOrderReview(@PathVariable UUID id);
}
