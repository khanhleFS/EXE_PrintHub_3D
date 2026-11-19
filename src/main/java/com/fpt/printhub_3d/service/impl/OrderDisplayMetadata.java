package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.review.CustomOrderReviewResponseDTO;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.CustomOrderReview;
import com.fpt.printhub_3d.entity.Enumeration.OrderRewardStatus;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.OrderRewardRecord;
import com.fpt.printhub_3d.entity.Review;
import com.fpt.printhub_3d.service.OrderRewardService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared metadata helpers for FE display of review eligibility (DG-01..DG-09)
 * and unified reward status/estimates (XU-01) across standalone and checkout DTOs.
 */
final class OrderDisplayMetadata {

    private static final BigDecimal REWARD_POINT_UNIT = new BigDecimal("10000");
    private static final long EDIT_WINDOW_DAYS = 7L;

    private OrderDisplayMetadata() {}

    record RewardSnapshot(int estimatedRewardPoints, String rewardStatus) {}

    record CustomReviewMeta(boolean canReview, boolean canEditReview, CustomOrderReviewResponseDTO review) {}

    record ItemReviewMeta(
            Long reviewId,
            Short reviewRating,
            boolean canReview,
            boolean canEditReview,
            String reviewComment,
            Instant reviewCreatedAt
    ) {}

    static CustomReviewMeta computeCustomReviewMeta(
            CustomOrder order,
            CustomOrderReview review,
            boolean isBuyerOwner,
            Instant now
    ) {
        boolean completed = order != null && "COMPLETED".equalsIgnoreCase(order.getStatus());
        if (review == null) {
            return new CustomReviewMeta(completed && isBuyerOwner, false, null);
        }
        Instant deadline = review.getCreatedAt() != null
                ? review.getCreatedAt().plus(EDIT_WINDOW_DAYS, ChronoUnit.DAYS)
                : null;
        boolean withinWindow = deadline != null && !now.isAfter(deadline);
        CustomOrderReviewResponseDTO dto = toCustomReviewDTO(review, now);
        return new CustomReviewMeta(false, isBuyerOwner && withinWindow, dto);
    }

    static CustomOrderReviewResponseDTO toCustomReviewDTO(CustomOrderReview review, Instant now) {
        if (review == null) {
            return null;
        }
        Instant deadline = review.getCreatedAt() != null
                ? review.getCreatedAt().plus(EDIT_WINDOW_DAYS, ChronoUnit.DAYS)
                : null;
        boolean canEdit = deadline != null && !now.isAfter(deadline);
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

    static ItemReviewMeta computeItemReviewMeta(
            Order order,
            Review itemReview,
            boolean isBuyerOwner,
            Instant now
    ) {
        boolean completed = order != null && "COMPLETED".equalsIgnoreCase(order.getStatus());
        if (itemReview == null) {
            return new ItemReviewMeta(null, null, completed && isBuyerOwner, false, null, null);
        }
        Instant deadline = itemReview.getCreatedAt() != null
                ? itemReview.getCreatedAt().plus(EDIT_WINDOW_DAYS, ChronoUnit.DAYS)
                : null;
        boolean withinWindow = deadline != null && !now.isAfter(deadline);
        return new ItemReviewMeta(
                itemReview.getId(),
                itemReview.getRating(),
                false,
                isBuyerOwner && withinWindow,
                itemReview.getComment(),
                itemReview.getCreatedAt()
        );
    }

    static RewardSnapshot computeRewardSnapshot(
            String orderStatus,
            boolean zeroAmountPromo,
            BigDecimal eligibleNetGoods,
            OrderRewardRecord existingRecord,
            Boolean legacyProcessed,
            Integer legacyPointsEarned,
            OrderRewardService rewardService
    ) {
        if (existingRecord != null) {
            int pts = existingRecord.getStatus() == OrderRewardStatus.PENDING_PAYMENT_EVIDENCE
                    ? estimatePoints(eligibleNetGoods, rewardService)
                    : (existingRecord.getPointsAwarded() != null ? existingRecord.getPointsAwarded() : 0);
            return new RewardSnapshot(pts, existingRecord.getStatus().name());
        }
        if (Boolean.TRUE.equals(legacyProcessed)) {
            int pts = legacyPointsEarned != null ? legacyPointsEarned : 0;
            return new RewardSnapshot(pts, pts > 0
                    ? OrderRewardStatus.GRANTED.name()
                    : OrderRewardStatus.EVALUATED_ZERO.name());
        }
        if (zeroAmountPromo
                || "CANCELLED".equalsIgnoreCase(orderStatus)
                || "EXPIRED".equalsIgnoreCase(orderStatus)) {
            return new RewardSnapshot(0, OrderRewardStatus.EVALUATED_ZERO.name());
        }
        return new RewardSnapshot(estimatePoints(eligibleNetGoods, rewardService), "ESTIMATED");
    }

    static int estimatePoints(BigDecimal eligibleNetGoods, OrderRewardService rewardService) {
        if (eligibleNetGoods == null || eligibleNetGoods.signum() <= 0) {
            return 0;
        }
        if (rewardService != null) {
            return rewardService.estimateRewardPoints(eligibleNetGoods);
        }
        return eligibleNetGoods.divideToIntegralValue(REWARD_POINT_UNIT).intValue();
    }

    static BigDecimal resolveStandaloneOrderNetGoods(Order order) {
        if (order == null || "CANCELLED".equalsIgnoreCase(order.getStatus())
                || "EXPIRED".equalsIgnoreCase(order.getStatus())) {
            return BigDecimal.ZERO;
        }
        BigDecimal baseMerch = order.getSubtotalAmount() != null
                ? order.getSubtotalAmount()
                : (order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
        BigDecimal discount = order.getDiscountAmount() != null && order.getDiscountAmount().signum() > 0
                ? order.getDiscountAmount()
                : BigDecimal.ZERO;
        return baseMerch.subtract(discount).max(BigDecimal.ZERO);
    }

    static BigDecimal resolveCustomLineNetGoods(CustomOrder custom) {
        if (custom == null || "CANCELLED".equalsIgnoreCase(custom.getStatus())
                || "EXPIRED".equalsIgnoreCase(custom.getStatus())) {
            return BigDecimal.ZERO;
        }
        BigDecimal sub = custom.getSubtotalAmount() != null && custom.getSubtotalAmount().signum() > 0
                ? custom.getSubtotalAmount()
                : OrderPricing.quotedLineTotal(custom);
        BigDecimal discount = custom.getDiscountAmount() != null && custom.getDiscountAmount().signum() > 0
                ? custom.getDiscountAmount()
                : BigDecimal.ZERO;
        return sub.subtract(discount).max(BigDecimal.ZERO);
    }
}
