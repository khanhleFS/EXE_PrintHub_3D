package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.ReviewDisputeErrorCode;
import com.fpt.printhub_3d.dto.review.ReviewCreateRequestDTO;
import com.fpt.printhub_3d.dto.review.ReviewResponseDTO;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Review;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.ReviewRepository;
import com.fpt.printhub_3d.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public ReviewResponseDTO createReview(ReviewCreateRequestDTO request, User reviewer) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ApiException(ReviewDisputeErrorCode.ORDER_NOT_FOUND));

        if (!order.getBuyer().getId().equals(reviewer.getId())) {
            throw new ApiException(ReviewDisputeErrorCode.NOT_ORDER_BUYER);
        }

        if (!"COMPLETED".equalsIgnoreCase(order.getStatus())) {
            throw new ApiException(ReviewDisputeErrorCode.ORDER_NOT_COMPLETED);
        }

        if (reviewRepository.existsByOrderId(order.getId())) {
            throw new ApiException(ReviewDisputeErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Review review = new Review();
        review.setReviewer(reviewer);
        review.setSeller(order.getSeller());
        review.setOrder(order);
        review.setRating(request.rating());
        review.setComment(request.comment());
        review.setCreatedAt(Instant.now());

        Review savedReview = reviewRepository.save(review);
        log.info("Tạo review [{}] cho order [{}]", savedReview.getId(), order.getId());
        return toResponse(savedReview);
    }

    private ReviewResponseDTO toResponse(Review review) {
        return ReviewResponseDTO.builder()
                .id(review.getId())
                .orderId(review.getOrder().getId())
                .reviewerId(review.getReviewer().getId())
                .reviewerName(review.getReviewer().getFullName())
                .sellerId(review.getSeller().getId())
                .sellerName(review.getSeller().getFullName())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
