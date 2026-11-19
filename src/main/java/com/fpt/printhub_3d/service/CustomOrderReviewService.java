package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.review.CustomOrderReviewRequestDTO;
import com.fpt.printhub_3d.dto.review.CustomOrderReviewResponseDTO;
import com.fpt.printhub_3d.entity.User;

import java.util.UUID;

public interface CustomOrderReviewService {
    CustomOrderReviewResponseDTO upsertCustomOrderReview(UUID customOrderId, CustomOrderReviewRequestDTO request, User reviewer);
    CustomOrderReviewResponseDTO getCustomOrderReview(UUID customOrderId, User currentUser);
}
