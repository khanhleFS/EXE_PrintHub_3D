package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.review.ProductReviewCreateRequestDTO;
import com.fpt.printhub_3d.dto.review.ProductReviewResponseDTO;
import com.fpt.printhub_3d.dto.review.ProductReviewSummaryDTO;
import com.fpt.printhub_3d.dto.review.ReviewCreateRequestDTO;
import com.fpt.printhub_3d.dto.review.ReviewResponseDTO;
import com.fpt.printhub_3d.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReviewService {
    ProductReviewResponseDTO createOrUpdateProductReview(ProductReviewCreateRequestDTO request, User reviewer);

    Page<ProductReviewResponseDTO> getProductReviews(UUID productId, Pageable pageable);

    ProductReviewSummaryDTO getProductReviewSummary(UUID productId);

    ReviewResponseDTO createReview(ReviewCreateRequestDTO request, User reviewer);
}
