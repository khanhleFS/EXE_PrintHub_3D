package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.review.ReviewCreateRequestDTO;
import com.fpt.printhub_3d.dto.review.ReviewResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/reviews")
@Tag(name = "Review APIs", description = "APIs for customer reviews")
public interface ReviewAPI {

    @Operation(
            summary = "Create Maker review",
            description = "Customer viết đánh giá kèm điểm sao tín nhiệm cho Maker sau khi đơn hàng hoàn tất.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping
    ResponseEntity<ApiResponse<ReviewResponseDTO>> createReview(
            @Valid @RequestBody ReviewCreateRequestDTO request);
}
