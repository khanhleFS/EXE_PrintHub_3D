package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.review.AdminCustomOrderReviewDTO;
import com.fpt.printhub_3d.dto.review.CustomOrderReviewRequestDTO;
import com.fpt.printhub_3d.dto.review.CustomOrderReviewResponseDTO;
import com.fpt.printhub_3d.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CustomOrderReviewService {
    CustomOrderReviewResponseDTO upsertCustomOrderReview(UUID customOrderId, CustomOrderReviewRequestDTO request, User reviewer);
    CustomOrderReviewResponseDTO getCustomOrderReview(UUID customOrderId, User currentUser);
    Page<AdminCustomOrderReviewDTO> getAdminCustomOrderReviews(Short rating, Pageable pageable);
}
