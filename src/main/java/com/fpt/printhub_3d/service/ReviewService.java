package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.review.ReviewCreateRequestDTO;
import com.fpt.printhub_3d.dto.review.ReviewResponseDTO;
import com.fpt.printhub_3d.entity.User;

public interface ReviewService {
    ReviewResponseDTO createReview(ReviewCreateRequestDTO request, User reviewer);
}
