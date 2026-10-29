package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.ReviewDisputeErrorCode;
import com.fpt.printhub_3d.dto.review.*;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import com.fpt.printhub_3d.entity.PointTransaction;
import com.fpt.printhub_3d.repository.PointTransactionRepository;
import com.fpt.printhub_3d.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final PointWalletRepository pointWalletRepository;
    private final PointTransactionRepository pointTransactionRepository;

    @Override
    @Transactional
    public ProductReviewResponseDTO createOrUpdateProductReview(ProductReviewCreateRequestDTO request, User reviewer) {
        OrderItem orderItem = orderItemRepository.findById(request.orderItemId())
                .orElseThrow(() -> new ApiException(ReviewDisputeErrorCode.ORDER_ITEM_NOT_FOUND));

        Order order = orderItem.getOrder();
        if (order == null) {
            throw new ApiException(ReviewDisputeErrorCode.ORDER_NOT_FOUND);
        }

        if (!order.getBuyer().getId().equals(reviewer.getId())) {
            throw new ApiException(ReviewDisputeErrorCode.NOT_ORDER_BUYER);
        }

        if (!"COMPLETED".equalsIgnoreCase(order.getStatus())) {
            throw new ApiException(ReviewDisputeErrorCode.ORDER_NOT_COMPLETED);
        }

        Optional<Review> existingOpt = reviewRepository.findByOrderItemId(orderItem.getId());
        Review savedReview;

        if (existingOpt.isPresent()) {
            Review existing = existingOpt.get();
            // Cho phép chỉnh sửa trong vòng 7 ngày kể từ ngày tạo
            if (existing.getCreatedAt().plus(7, ChronoUnit.DAYS).isBefore(Instant.now())) {
                throw new ApiException(ReviewDisputeErrorCode.REVIEW_EDIT_EXPIRED);
            }

            existing.setRating(request.rating());
            existing.setComment(request.comment());
            if (request.imageUrls() != null) {
                existing.setImageUrls(new ArrayList<>(request.imageUrls()));
            }
            existing.setUpdatedAt(Instant.now());
            savedReview = reviewRepository.save(existing);
            log.info("Cập nhật đánh giá [{}] cho orderItem [{}]", savedReview.getId(), orderItem.getId());
        } else {
            Review review = new Review();
            review.setReviewer(reviewer);
            review.setOrder(order);
            review.setOrderItem(orderItem);
            review.setProduct(orderItem.getProduct());
            review.setRating(request.rating());
            review.setComment(request.comment());
            if (request.imageUrls() != null) {
                review.setImageUrls(new ArrayList<>(request.imageUrls()));
            }
            review.setCreatedAt(Instant.now());
            savedReview = reviewRepository.save(review);
            log.info("Tạo đánh giá mới [{}] cho sản phẩm [{}] từ orderItem [{}]",
                    savedReview.getId(), orderItem.getProduct().getId(), orderItem.getId());

            // Thưởng 20 điểm tích lũy khi đánh giá kèm hình ảnh thực tế
            if (request.imageUrls() != null && !request.imageUrls().isEmpty()) {
                rewardPointsForReview(reviewer, 20);
            }
        }

        // Tự động tính toán lại averageRating & reviewCount cho sản phẩm
        recalculateProductRating(orderItem.getProduct());

        return toProductReviewResponse(savedReview);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductReviewResponseDTO> getProductReviews(UUID productId, Pageable pageable) {
        Page<Review> reviews = reviewRepository.findByProductId(productId, pageable);
        return reviews.map(this::toProductReviewResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductReviewSummaryDTO getProductReviewSummary(UUID productId) {
        Double avg = reviewRepository.calculateAverageRatingByProductId(productId);
        double roundedAvg = (avg != null) ? Math.round(avg * 10.0) / 10.0 : 0.0;
        long total = reviewRepository.countByProductId(productId);

        long c5 = reviewRepository.countByProductIdAndRating(productId, (short) 5);
        long c4 = reviewRepository.countByProductIdAndRating(productId, (short) 4);
        long c3 = reviewRepository.countByProductIdAndRating(productId, (short) 3);
        long c2 = reviewRepository.countByProductIdAndRating(productId, (short) 2);
        long c1 = reviewRepository.countByProductIdAndRating(productId, (short) 1);

        return ProductReviewSummaryDTO.builder()
                .productId(productId)
                .averageRating(roundedAvg)
                .reviewCount((int) total)
                .count5Star(c5)
                .count4Star(c4)
                .count3Star(c3)
                .count2Star(c2)
                .count1Star(c1)
                .build();
    }

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
        review.setOrder(order);
        review.setRating(request.rating());
        review.setComment(request.comment());
        review.setCreatedAt(Instant.now());

        // Lấy sản phẩm đầu tiên của đơn nếu có để liên kết
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        if (!items.isEmpty()) {
            review.setOrderItem(items.getFirst());
            review.setProduct(items.getFirst().getProduct());
        }

        Review savedReview = reviewRepository.save(review);
        if (review.getProduct() != null) {
            recalculateProductRating(review.getProduct());
        }

        log.info("Tạo review đơn hàng [{}] cho order [{}]", savedReview.getId(), order.getId());
        return toOrderReviewResponse(savedReview);
    }

    // ──────────────────────────── Private Helpers ────────────────────────────

    private void recalculateProductRating(Product product) {
        Double avg = reviewRepository.calculateAverageRatingByProductId(product.getId());
        double roundedAvg = (avg != null) ? Math.round(avg * 10.0) / 10.0 : 0.0;
        long count = reviewRepository.countByProductId(product.getId());

        product.setAverageRating(roundedAvg);
        product.setReviewCount((int) count);
        productRepository.save(product);
        log.info("Tính lại rating cho sản phẩm [{}]: avg={}, count={}", product.getId(), roundedAvg, count);
    }

    private void rewardPointsForReview(User reviewer, int points) {
        if (points <= 0) return;
        PointWallet wallet = pointWalletRepository.findByUserIdForUpdate(reviewer.getId()).orElseGet(() -> {
            PointWallet newWallet = PointWallet.builder()
                    .userId(reviewer.getId())
                    .user(reviewer)
                    .balance(0)
                    .isNew(true)
                    .updatedAt(LocalDateTime.now())
                    .build();
            reviewer.setPointWallet(newWallet);
            return pointWalletRepository.save(newWallet);
        });
        int currentBalance = wallet.getBalance() != null ? wallet.getBalance() : 0;
        wallet.setBalance(currentBalance + points);
        wallet.setUpdatedAt(LocalDateTime.now());
        wallet = pointWalletRepository.save(wallet);
        reviewer.setPointWallet(wallet);

        pointTransactionRepository.save(PointTransaction.builder()
                .pointWallet(wallet)
                .amount(points)
                .type(PointTransactionType.EARN)
                .description("Thưởng điểm đánh giá sản phẩm có hình ảnh")
                .createdAt(LocalDateTime.now())
                .build());

        log.info("Thưởng [{}] điểm tích lũy cho người dùng [{}] vì gửi review kèm ảnh", points, reviewer.getId());
    }

    private ProductReviewResponseDTO toProductReviewResponse(Review review) {
        String color = review.getOrderItem() != null ? review.getOrderItem().getColor() : null;
        String engraving = review.getOrderItem() != null ? review.getOrderItem().getEngravingText() : null;
        String productTitle = review.getProduct() != null ? review.getProduct().getTitle() : "";

        return ProductReviewResponseDTO.builder()
                .id(review.getId())
                .productId(review.getProduct() != null ? review.getProduct().getId() : null)
                .productTitle(productTitle)
                .orderId(review.getOrder() != null ? review.getOrder().getId() : null)
                .orderItemId(review.getOrderItem() != null ? review.getOrderItem().getId() : null)
                .reviewerId(review.getReviewer().getId())
                .reviewerName(review.getReviewer().getFullName())
                .reviewerAvatar(null)
                .rating(review.getRating())
                .comment(review.getComment())
                .imageUrls(review.getImageUrls() != null ? new ArrayList<>(review.getImageUrls()) : new ArrayList<>())
                .color(color)
                .engravingText(engraving)
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    private ReviewResponseDTO toOrderReviewResponse(Review review) {
        return ReviewResponseDTO.builder()
                .id(review.getId())
                .orderId(review.getOrder().getId())
                .reviewerId(review.getReviewer().getId())
                .reviewerName(review.getReviewer().getFullName())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
