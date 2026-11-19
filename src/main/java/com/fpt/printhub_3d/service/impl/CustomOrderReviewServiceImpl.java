package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.ReviewDisputeErrorCode;
import com.fpt.printhub_3d.dto.review.CustomOrderReviewRequestDTO;
import com.fpt.printhub_3d.dto.review.CustomOrderReviewResponseDTO;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.CustomOrderReview;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.repository.CustomOrderReviewRepository;
import com.fpt.printhub_3d.service.CustomOrderReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class CustomOrderReviewServiceImpl implements CustomOrderReviewService {

    public static final int EDIT_WINDOW_DAYS = 7;
    public static final int MAX_COMMENT_LENGTH = 2000;
    public static final int MAX_IMAGES = 5;

    private final CustomOrderRepository customOrderRepository;
    private final CustomOrderReviewRepository customOrderReviewRepository;
    private final Clock clock;

    @Autowired
    public CustomOrderReviewServiceImpl(
            CustomOrderRepository customOrderRepository,
            CustomOrderReviewRepository customOrderReviewRepository,
            @Autowired(required = false) Clock clock
    ) {
        this.customOrderRepository = customOrderRepository;
        this.customOrderReviewRepository = customOrderReviewRepository;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    @Transactional
    public CustomOrderReviewResponseDTO upsertCustomOrderReview(
            UUID customOrderId,
            CustomOrderReviewRequestDTO request,
            User reviewer
    ) {
        if (reviewer == null) {
            throw new ApiException(CommonErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập để gửi đánh giá");
        }
        validateRequest(request);

        // Khóa dòng CustomOrder trước khi kiểm tra/lưu để chống race condition gửi đồng thời (DG-06)
        CustomOrder order = customOrderRepository.findLockedById(customOrderId)
                .orElseThrow(() -> new ApiException(ReviewDisputeErrorCode.ORDER_NOT_FOUND,
                        "Không tìm thấy dòng thiết kế in custom ID: " + customOrderId));

        if (order.getBuyer() == null || !order.getBuyer().getId().equals(reviewer.getId())) {
            throw new ApiException(ReviewDisputeErrorCode.NOT_ORDER_BUYER);
        }

        // Chặn mọi dòng chưa hoàn tất hoặc dòng đã hủy nằm trong checkout đã hoàn tất (DG-04)
        if (!"COMPLETED".equalsIgnoreCase(order.getStatus())) {
            throw new ApiException(ReviewDisputeErrorCode.ORDER_NOT_COMPLETED,
                    "Chỉ có thể đánh giá dòng thiết kế đã giao hàng hoàn tất (COMPLETED)");
        }

        Instant now = clock.instant();
        List<String> sanitizedImages = sanitizeImages(request.imageUrls());

        Optional<CustomOrderReview> existingOpt = customOrderReviewRepository.findByCustomOrderId(customOrderId);
        CustomOrderReview saved;

        if (existingOpt.isPresent()) {
            CustomOrderReview existing = existingOpt.get();
            Instant deadline = existing.getCreatedAt().plus(EDIT_WINDOW_DAYS, ChronoUnit.DAYS);
            if (now.isAfter(deadline)) {
                throw new ApiException(ReviewDisputeErrorCode.REVIEW_EDIT_EXPIRED);
            }
            existing.setRating(request.rating());
            existing.setComment(request.comment());
            if (request.imageUrls() != null) {
                existing.setImageUrls(sanitizedImages);
            }
            existing.setUpdatedAt(now);
            saved = customOrderReviewRepository.save(existing);
            log.info("Cập nhật đánh giá dịch vụ in custom [{}] cho dòng [{}]", saved.getId(), customOrderId);
        } else {
            CustomOrderReview review = new CustomOrderReview();
            review.setCustomOrder(order);
            review.setReviewer(reviewer);
            review.setRating(request.rating());
            review.setComment(request.comment());
            review.setImageUrls(sanitizedImages);
            review.setCreatedAt(now);
            review.setUpdatedAt(now);
            saved = customOrderReviewRepository.save(review);
            log.info("Tạo đánh giá dịch vụ in custom [{}] cho dòng [{}] (0 xu bổ sung, cô lập khỏi sao catalog)",
                    saved.getId(), customOrderId);
        }

        return toResponse(saved, now);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomOrderReviewResponseDTO getCustomOrderReview(UUID customOrderId, User currentUser) {
        if (currentUser == null) {
            throw new ApiException(CommonErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập để xem đánh giá");
        }
        CustomOrder order = customOrderRepository.findById(customOrderId)
                .orElseThrow(() -> new ApiException(ReviewDisputeErrorCode.ORDER_NOT_FOUND,
                        "Không tìm thấy dòng thiết kế in custom ID: " + customOrderId));

        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
        boolean isOwner = order.getBuyer() != null && order.getBuyer().getId().equals(currentUser.getId());
        if (!isAdmin && !isOwner) {
            throw new ApiException(ReviewDisputeErrorCode.NOT_ORDER_BUYER);
        }

        CustomOrderReview review = customOrderReviewRepository.findByCustomOrderId(customOrderId)
                .orElseThrow(() -> new ApiException(ReviewDisputeErrorCode.REVIEW_NOT_FOUND));

        return toResponse(review, clock.instant());
    }

    private void validateRequest(CustomOrderReviewRequestDTO request) {
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
    }

    private List<String> sanitizeImages(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> clean = new ArrayList<>();
        for (String url : raw) {
            if (url != null && !url.isBlank()) {
                clean.add(url.trim());
            }
        }
        return clean;
    }

    private CustomOrderReviewResponseDTO toResponse(CustomOrderReview review, Instant now) {
        Instant deadline = review.getCreatedAt().plus(EDIT_WINDOW_DAYS, ChronoUnit.DAYS);
        boolean canEdit = !now.isAfter(deadline);
        CustomOrder order = review.getCustomOrder();
        return CustomOrderReviewResponseDTO.builder()
                .id(review.getId())
                .customOrderId(order != null ? order.getId() : null)
                .checkoutOrderId(order != null && order.getCheckoutOrder() != null
                        ? order.getCheckoutOrder().getId() : null)
                .reviewTargetType("CUSTOM_ORDER")
                .reviewerId(review.getReviewer() != null ? review.getReviewer().getId() : null)
                .reviewerName(review.getReviewer() != null ? review.getReviewer().getFullName() : null)
                .rating(review.getRating())
                .comment(review.getComment())
                .imageUrls(review.getImageUrls() != null ? new ArrayList<>(review.getImageUrls()) : List.of())
                .canEdit(canEdit)
                .editDeadlineAt(deadline)
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
