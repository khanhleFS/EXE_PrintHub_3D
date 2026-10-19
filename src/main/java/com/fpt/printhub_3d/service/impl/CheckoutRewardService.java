package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import com.fpt.printhub_3d.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Service quản lý tích điểm thưởng tập trung cho CheckoutOrder (R2-04 / P1.6 / P1.7).
 * Đảm bảo tính toán lũy kế toàn bộ tiền hàng hợp lệ đã thanh toán và hoàn tất,
 * trừ đi số điểm đã cấp trước đó, tránh cấp trùng điểm giữa marketplace và custom.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutRewardService {

    private final CheckoutOrderRepository checkoutOrderRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomOrderRepository customOrderRepository;
    private final PaymentRepository paymentRepository;
    private final PointWalletRepository pointWalletRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final UserRepository userRepository;

    private static final BigDecimal REWARD_POINT_UNIT = BigDecimal.valueOf(10000);

    @Transactional
    public int rewardCheckoutOrderCumulative(UUID checkoutOrderId, User buyer) {
        if (checkoutOrderId == null || buyer == null) {
            return 0;
        }

        // Global lock hierarchy: User -> CheckoutOrder -> PointWallet
        User lockedBuyer = userRepository.findLockedById(buyer.getId()).orElse(buyer);

        CheckoutOrder co = checkoutOrderRepository.findLockedById(checkoutOrderId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy CheckoutOrder"));

        // Kiểm tra thanh toán thành công theo từng nhóm scope - bắt buộc entity Payment có status = 'SUCCESS'
        boolean marketplacePaid = paymentRepository.findByCheckoutOrderIdAndScope(co.getId(), "MARKETPLACE")
                .filter(p -> "SUCCESS".equals(p.getStatus())).isPresent()
                || paymentRepository.findByCheckoutOrderIdAndScope(co.getId(), "ALL")
                .filter(p -> "SUCCESS".equals(p.getStatus())).isPresent();

        boolean customPaid = paymentRepository.findByCheckoutOrderIdAndScope(co.getId(), "CUSTOM")
                .filter(p -> "SUCCESS".equals(p.getStatus())).isPresent()
                || paymentRepository.findByCheckoutOrderIdAndScope(co.getId(), "ALL")
                .filter(p -> "SUCCESS".equals(p.getStatus())).isPresent();

        List<Order> childOrders = orderRepository.findByCheckoutOrderId(co.getId());
        List<CustomOrder> childCustoms = (customOrderRepository != null)
                ? customOrderRepository.findByCheckoutOrderId(co.getId()) : Collections.emptyList();

        BigDecimal cumulativeGoods = BigDecimal.ZERO;

        for (Order o : childOrders) {
            boolean orderPaid = marketplacePaid || paymentRepository.findByOrderId(o.getId())
                    .filter(p -> "SUCCESS".equals(p.getStatus())).isPresent();
            if ("COMPLETED".equals(o.getStatus()) && orderPaid) {
                List<OrderItem> ois = orderItemRepository.findByOrderId(o.getId());
                if (ois != null && !ois.isEmpty()) {
                    for (OrderItem oi : ois) {
                        if (oi.getUnitPrice() != null && oi.getQuantity() != null) {
                            cumulativeGoods = cumulativeGoods.add(oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity())));
                        }
                    }
                } else if (o.getTotalAmount() != null) {
                    cumulativeGoods = cumulativeGoods.add(o.getTotalAmount());
                }
            }
        }

        for (CustomOrder c : childCustoms) {
            boolean customOrderPaid = customPaid || paymentRepository.findByCustomOrderId(c.getId())
                    .filter(p -> "SUCCESS".equals(p.getStatus())).isPresent();
            if ("COMPLETED".equals(c.getStatus()) && customOrderPaid && c.getQuotedPrice() != null) {
                int q = (c.getQuantity() != null && c.getQuantity() > 0) ? c.getQuantity() : 1;
                BigDecimal price = (c.getPriceVersion() != null && c.getPriceVersion() >= 2)
                        ? c.getQuotedPrice().multiply(BigDecimal.valueOf(q))
                        : c.getQuotedPrice();
                cumulativeGoods = cumulativeGoods.add(price);
            }
        }

        int totalEligiblePoints = cumulativeGoods.divideToIntegralValue(REWARD_POINT_UNIT).intValue();
        int previouslyAwarded = co.getRewardPointsAwarded() != null ? co.getRewardPointsAwarded() : 0;
        int deltaPoints = Math.max(0, totalEligiblePoints - previouslyAwarded);

        if (deltaPoints > 0) {
            co.setRewardPointsAwarded(previouslyAwarded + deltaPoints);
            co.setUpdatedAt(Instant.now());
            checkoutOrderRepository.save(co);

            PointWallet wallet = pointWalletRepository.findById(lockedBuyer.getId()).orElseGet(() -> {
                PointWallet newWallet = PointWallet.builder()
                        .userId(lockedBuyer.getId())
                        .user(lockedBuyer)
                        .balance(0)
                        .isNew(true)
                        .build();
                lockedBuyer.setPointWallet(newWallet);
                return newWallet;
            });

            wallet.setBalance(wallet.getBalance() + deltaPoints);
            wallet.setUpdatedAt(LocalDateTime.now());
            wallet = pointWalletRepository.save(wallet);
            lockedBuyer.setPointWallet(wallet);

            String desc = "Tích điểm hoàn thành đơn " + co.getId() + " (+ " + deltaPoints + " điểm)";
            pointTransactionRepository.save(PointTransaction.builder()
                    .pointWallet(wallet)
                    .amount(deltaPoints)
                    .type(PointTransactionType.EARN)
                    .description(desc)
                    .createdAt(LocalDateTime.now())
                    .build());

            log.info("R2-04 P1.6: Đã cấp thêm {} điểm lũy kế (tổng {}) cho buyer {} từ CheckoutOrder {}",
                    deltaPoints, previouslyAwarded + deltaPoints, buyer.getId(), co.getId());
        }

        return deltaPoints;
    }
}
