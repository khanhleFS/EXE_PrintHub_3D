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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class ReviewServiceImpl implements ReviewService {

    public static final int MAX_PUBLIC_REVIEWS = 10;
    public static final int MAX_COMMENT_LENGTH = 2000;
    public static final int MAX_IMAGES = 5;
    public static final String REVIEW_PHOTO_SOURCE_TYPE = "ORDER_ITEM";
    public static final String REVIEW_PHOTO_REWARD_TYPE = "REVIEW_PHOTO_BONUS";

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final PointWalletRepository pointWalletRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final PointWalletLockHelper pointWalletLockHelper;
    private final Clock clock;

    @Autowired
    public ReviewServiceImpl(
            ReviewRepository reviewRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            PointWalletRepository pointWalletRepository,
            PointTransactionRepository pointTransactionRepository,
            @Autowired(required = false) PointWalletLockHelper pointWalletLockHelper,
            @Autowired(required = false) Clock clock
    ) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.pointWalletRepository = pointWalletRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.pointWalletLockHelper = pointWalletLockHelper;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public ReviewServiceImpl(
            ReviewRepository reviewRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            PointWalletRepository pointWalletRepository,
            PointTransactionRepository pointTransactionRepository
    ) {
        this(reviewRepository, orderRepository, orderItemRepository, productRepository,
                pointWalletRepository, pointTransactionRepository, null, Clock.systemUTC());
    }

    @Override
    @Transactional
    public ProductReviewResponseDTO createOrUpdateProductReview(ProductReviewCreateRequestDTO request, User reviewer) {
        if (request == null || request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new ApiException(ReviewDisputeErrorCode.INVALID_REVIEW_INPUT,
                    "Số sao đánh giá phải là số nguyên từ 1 đến 5");
        }
        if (request.comment() != null && request.comment().length() > MAX_COMMENT_LENGTH) {
            throw new ApiException(ReviewDisputeErrorCode.INVALID_REVIEW_INPUT,
                    "Nội dung nhận xét tối đa " + MAX_COMMENT_LENGTH + " ký tự");
        }
        if (request.imageUrls() != null && request.imageUrls().size() > MAX_IMAGES) {
            throw new ApiException(ReviewDisputeErrorCode.INVALID_REVIEW_INPUT,
                    "Tối đa " + MAX_IMAGES + " hình ảnh đính kèm");
        }

        OrderItem orderItem = orderItemRepository.findById(request.orderItemId())
                .orElseThrow(() -> new ApiException(ReviewDisputeErrorCode.ORDER_ITEM_NOT_FOUND));

        Order order = orderItem.getOrder();
        if (order == null) {
            throw new ApiException(ReviewDisputeErrorCode.ORDER_NOT_FOUND);
        }

        // Khóa đơn hàng để chống race condition tạo trùng đánh giá & thưởng ảnh (DG-06)
        order = orderRepository.findLockedById(order.getId()).orElse(order);

        if (!order.getBuyer().getId().equals(reviewer.getId())) {
            throw new ApiException(ReviewDisputeErrorCode.NOT_ORDER_BUYER);
        }

        if (!"COMPLETED".equalsIgnoreCase(order.getStatus())) {
            throw new ApiException(ReviewDisputeErrorCode.ORDER_NOT_COMPLETED);
        }

        Instant now = clock.instant();
        Optional<Review> existingOpt = reviewRepository.findByOrderItemId(orderItem.getId());
        Review savedReview;

        if (existingOpt.isPresent()) {
            Review existing = existingOpt.get();
            // Cho phép chỉnh sửa trong vòng 7 ngày kể từ ngày tạo (DG-05)
            if (now.isAfter(existing.getCreatedAt().plus(7, ChronoUnit.DAYS))) {
                throw new ApiException(ReviewDisputeErrorCode.REVIEW_EDIT_EXPIRED);
            }

            existing.setRating(request.rating());
            existing.setComment(request.comment());
            if (request.imageUrls() != null) {
                existing.setImageUrls(new ArrayList<>(request.imageUrls()));
            }
            existing.setUpdatedAt(now);
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
            review.setIsPublic(false);
            review.setCreatedAt(now);
            savedReview = reviewRepository.save(review);
            log.info("Tạo đánh giá mới [{}] cho sản phẩm [{}] từ orderItem [{}]",
                    savedReview.getId(), orderItem.getProduct().getId(), orderItem.getId());

            // Thưởng 20 điểm tích lũy DUY NHẤT 1 lần khi tạo mới đánh giá có hình ảnh thực tế (DG-08)
            boolean hasValidImage = request.imageUrls() != null
                    && request.imageUrls().stream().anyMatch(u -> u != null && !u.isBlank());
            if (hasValidImage) {
                rewardPointsForReview(reviewer, orderItem.getId(), 20, now);
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
    public Page<ProductReviewResponseDTO> getAdminReviews(Short rating, Boolean isPublic, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), 50),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Review> reviews;
        if (rating == null && isPublic == null) {
            reviews = reviewRepository.findAllBy(newestFirst);
        } else if (rating != null && isPublic == null) {
            reviews = reviewRepository.findByRating(rating, newestFirst);
        } else if (rating == null) {
            reviews = Boolean.TRUE.equals(isPublic)
                    ? reviewRepository.findByIsPublicTrue(newestFirst)
                    : reviewRepository.findHiddenReviews(newestFirst);
        } else {
            reviews = Boolean.TRUE.equals(isPublic)
                    ? reviewRepository.findByRatingAndIsPublicTrue(rating, newestFirst)
                    : reviewRepository.findHiddenReviewsByRating(rating, newestFirst);
        }
        return reviews.map(this::toProductReviewResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReviewSummaryDTO getAdminReviewSummary() {
        long total = reviewRepository.count();
        long publicCount = reviewRepository.countByIsPublicTrue();
        long hiddenCount = Math.max(0L, total - publicCount);
        return AdminReviewSummaryDTO.builder()
                .totalReviews(total)
                .publicCount(publicCount)
                .hiddenCount(hiddenCount)
                .maxPublicLimit(MAX_PUBLIC_REVIEWS)
                .build();
    }

    @Override
    @Transactional
    public ReviewVisibilityResponseDTO updateReviewVisibility(Long reviewId, boolean isPublic) {
        List<Review> currentPublicReviews = reviewRepository.findAllPublicLocked();
        Review review = reviewRepository.findByIdLocked(reviewId)
                .orElseThrow(() -> new ApiException(ReviewDisputeErrorCode.REVIEW_NOT_FOUND));

        boolean alreadyPublic = Boolean.TRUE.equals(review.getIsPublic());
        long nextPublicCount = currentPublicReviews.size();

        if (isPublic && !alreadyPublic) {
            if (currentPublicReviews.size() >= MAX_PUBLIC_REVIEWS) {
                throw new ApiException(ReviewDisputeErrorCode.REVIEW_PUBLIC_LIMIT_REACHED);
            }
            review.setIsPublic(true);
            review.setPublicUpdatedAt(Instant.now());
            review = reviewRepository.save(review);
            nextPublicCount = currentPublicReviews.size() + 1L;
            log.info("Admin bật công khai đánh giá [{}], tổng công khai: {}/{}", reviewId, nextPublicCount, MAX_PUBLIC_REVIEWS);
        } else if (!isPublic && alreadyPublic) {
            review.setIsPublic(false);
            review.setPublicUpdatedAt(Instant.now());
            review = reviewRepository.save(review);
            nextPublicCount = Math.max(0L, currentPublicReviews.size() - 1L);
            log.info("Admin ẩn đánh giá [{}] khỏi trang chủ, tổng công khai: {}/{}", reviewId, nextPublicCount, MAX_PUBLIC_REVIEWS);
        }

        return ReviewVisibilityResponseDTO.builder()
                .review(toProductReviewResponse(review))
                .publicCount(nextPublicCount)
                .maxPublicLimit(MAX_PUBLIC_REVIEWS)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeaturedProductReviewDTO> getFeaturedReviews() {
        return reviewRepository.findFeaturedReviews(PageRequest.of(0, MAX_PUBLIC_REVIEWS)).stream()
                .map(review -> new FeaturedProductReviewDTO(
                        review.getId(),
                        review.getProduct() != null ? review.getProduct().getTitle() : "Sản phẩm PrintHub 3D",
                        firstNameForFeaturedReview(review.getReviewer().getFullName()),
                        review.getRating(),
                        review.getComment(),
                        review.getImageUrls().stream()
                                .filter(url -> url != null && (url.startsWith("https://") || url.startsWith("http://")))
                                .distinct()
                                .limit(5)
                                .toList(),
                        review.getCreatedAt()))
                .toList();
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
        review.setIsPublic(false);
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
    private String firstNameForFeaturedReview(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Khách hàng PrintHub";
        }
        String[] names = fullName.strip().split("\\s+");
        return names[names.length - 1];
    }


    private void recalculateProductRating(Product product) {
        Double avg = reviewRepository.calculateAverageRatingByProductId(product.getId());
        double roundedAvg = (avg != null) ? Math.round(avg * 10.0) / 10.0 : 0.0;
        long count = reviewRepository.countByProductId(product.getId());

        product.setAverageRating(roundedAvg);
        product.setReviewCount((int) count);
        productRepository.save(product);
        log.info("Tính lại rating cho sản phẩm [{}]: avg={}, count={}", product.getId(), roundedAvg, count);
    }

    private void rewardPointsForReview(User reviewer, Long orderItemId, int points, Instant now) {
        if (points <= 0 || orderItemId == null) return;
        String sourceId = orderItemId.toString();
        if (pointTransactionRepository.existsBySourceTypeAndSourceIdAndRewardType(
                REVIEW_PHOTO_SOURCE_TYPE, sourceId, REVIEW_PHOTO_REWARD_TYPE)) {
            log.info("Bỏ qua thưởng review ảnh trùng lặp cho orderItem [{}]", orderItemId);
            return;
        }

        LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        PointWallet wallet = pointWalletLockHelper != null
                ? pointWalletLockHelper.lockOrCreateWallet(reviewer, clock)
                : pointWalletRepository.findByUserIdForUpdate(reviewer.getId()).orElseGet(() -> {
                    PointWallet newWallet = PointWallet.builder()
                            .userId(reviewer.getId())
                            .user(reviewer)
                            .balance(0)
                            .isNew(true)
                            .updatedAt(nowUtc)
                            .build();
                    reviewer.setPointWallet(newWallet);
                    return pointWalletRepository.save(newWallet);
                });

        int currentBalance = wallet.getBalance() != null ? wallet.getBalance() : 0;
        wallet.setBalance(currentBalance + points);
        wallet.setUpdatedAt(nowUtc);
        wallet = pointWalletRepository.save(wallet);
        reviewer.setPointWallet(wallet);

        pointTransactionRepository.save(PointTransaction.builder()
                .pointWallet(wallet)
                .amount(points)
                .type(PointTransactionType.EARN)
                .sourceType(REVIEW_PHOTO_SOURCE_TYPE)
                .sourceId(sourceId)
                .rewardType(REVIEW_PHOTO_REWARD_TYPE)
                .description("Thưởng điểm đánh giá sản phẩm có hình ảnh")
                .createdAt(nowUtc)
                .build());

        log.info("Thưởng [{}] điểm tích lũy cho người dùng [{}] vì gửi review kèm ảnh (orderItem={})",
                points, reviewer.getId(), orderItemId);
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
                .isPublic(Boolean.TRUE.equals(review.getIsPublic()))
                .publicUpdatedAt(review.getPublicUpdatedAt())
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
