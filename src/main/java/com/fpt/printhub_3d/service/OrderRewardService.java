package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.order.RewardCompletionResponseDTO;
import com.fpt.printhub_3d.entity.Enumeration.OrderRewardStatus;
import com.fpt.printhub_3d.entity.User;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Nghiệp vụ thống nhất tính & cộng xu khi đơn hàng hoàn tất (Luồng 03).
 * Chuẩn hóa nguồn về CheckoutOrder cha (nếu có) hoặc đơn độc lập, khóa ví an toàn, chống thưởng trùng.
 */
public interface OrderRewardService {

    record RewardEvaluation(
            PaymentEvidenceService.SourceType canonicalSourceType,
            UUID canonicalSourceId,
            UUID buyerId,
            String buyerName,
            BigDecimal eligibleNetAmount,
            int pointsAwarded,
            int walletBalance,
            OrderRewardStatus status,
            boolean alreadyProcessed,
            String message
    ) {
        public RewardCompletionResponseDTO toLegacyResponse() {
            return RewardCompletionResponseDTO.builder()
                    .orderId(canonicalSourceId)
                    .customerId(buyerId)
                    .customerName(buyerName)
                    .rewardPointsEarned(pointsAwarded)
                    .totalRewardPoints(walletBalance)
                    .alreadyProcessed(alreadyProcessed)
                    .build();
        }
    }

    /**
     * Xử lý cộng xu cho nguồn bất kỳ (CheckoutOrder / Order / CustomOrder).
     * Nếu ID truyền vào thuộc đơn con của một CheckoutOrder, tự động quy về CheckoutOrder cha.
     */
    RewardEvaluation processCompletionReward(PaymentEvidenceService.SourceType requestedType, UUID requestedId);

    /**
     * Ghi nhận thu tiền COD idempotent qua PaymentEvidenceService rồi xét thưởng xu trong cùng giao dịch.
     */
    RewardEvaluation completeCodAndReward(
            PaymentEvidenceService.SourceType requestedType,
            UUID requestedId,
            BigDecimal collectedAmount,
            User admin
    );

    /**
     * Thử xét thưởng lại cho đơn trước đó đang PENDING_PAYMENT_EVIDENCE sau khi chứng từ thanh toán được đối soát (XU-05).
     */
    RewardEvaluation retryRewardAfterPaymentSettled(PaymentEvidenceService.SourceType sourceType, UUID sourceId);

    /**
     * Ước tính số xu dự kiến (floor(max(0, netGoods) / 10_000), loại phí giao và dòng hủy).
     */
    int estimateRewardPoints(BigDecimal eligibleNetGoodsAmount);
}
