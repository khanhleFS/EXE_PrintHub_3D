package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.security.CustomUserDetail;
import com.fpt.printhub_3d.controller.api.ReviewAPI;
import com.fpt.printhub_3d.dto.review.ReviewCreateRequestDTO;
import com.fpt.printhub_3d.dto.review.ReviewResponseDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ReviewController implements ReviewAPI {

    private final ReviewService reviewService;

    @Override
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ReviewResponseDTO>> createReview(ReviewCreateRequestDTO request) {
        CustomUserDetail userDetail = (CustomUserDetail) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        User reviewer = userDetail.getUser();

        ReviewResponseDTO response = reviewService.createReview(request, reviewer);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ReviewResponseDTO>builder()
                        .code(201)
                        .message("Tạo đánh giá Maker thành công")
                        .result(response)
                        .build());
    }
}
