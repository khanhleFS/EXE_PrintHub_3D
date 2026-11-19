package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.OrderErrorCode;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.OrderRewardStatus;
import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.OrderRewardService;
import com.fpt.printhub_3d.service.PaymentEvidenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class OrderRewardServiceImpl implements OrderRewardService {

    public static final BigDecimal REWARD_POINT_UNIT = new BigDecimal("10000");

    private final CheckoutOrderRepository checkoutOrderRepository;
    private final OrderRepository orderRepository;
    private final CustomOrderRepository customOrderRepository;
    private final OrderRewardRecordRepository orderRewardRecordRepository;
    private final PointWalletRepository pointWalletRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final PointWalletLockHelper pointWalletLockHelper;
    private final PaymentEvidenceService paymentEvidenceService;
    private final Clock clock;

    @Autowired
    public OrderRewardServiceImpl(
            CheckoutOrderRepository checkoutOrderRepository,
            OrderRepository orderRepository,
            CustomOrderRepository customOrderRepository,
            OrderRewardRecordRepository orderRewardRecordRepository,
            PointWalletRepository pointWalletRepository,
            PointTransactionRepository pointTransactionRepository,
            PointWalletLockHelper pointWalletLockHelper,
            PaymentEvidenceService paymentEvidenceService,
            @Autowired(required = false) Clock clock
    ) {
        this.checkoutOrderRepository = checkoutOrderRepository;
        this.orderRepository = orderRepository;
        this.customOrderRepository = customOrderRepository;
        this.orderRewardRecordRepository = orderRewardRecordRepository;
        this.pointWalletRepository = pointWalletRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.pointWalletLockHelper = pointWalletLockHelper;
        this.paymentEvidenceService = paymentEvidenceService;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    @Transactional
    public RewardEvaluation processCompletionReward(PaymentEvidenceService.SourceType requestedType, UUID requestedId) {
        if (requestedType == null || requestedId == null) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Thiếu thông tin nguồn đơn hàng để xét thưởng xu");
        }

        // 1. Chuẩn hóa nguồn về CheckoutOrder cha nếu đơn con thuộc checkout (XU-03, XU-06)
        CanonicalSource canonical = resolveAndLockCanonicalSource(requestedType, requestedId);

        if (!"COMPLETED".equalsIgnoreCase(canonical.status())) {
            throw new ApiException(OrderErrorCode.ORDER_NOT_COMPLETED,
                    "Đơn hàng chưa ở trạng thái COMPLETED để xét thưởng xu");
        }

        User buyer = canonical.buyer();
        if (buyer == null) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT, "Không xác định được người mua của đơn hàng");
        }

        String sourceTypeName = canonical.sourceType().name();
        UUID sourceId = canonical.sourceId();

        // 2. Kiểm tra bản ghi khóa duy nhất (source_type, source_id, ORDER_COMPLETION) (XU-06)
        Optional<OrderRewardRecord> existingOpt = orderRewardRecordRepository
                .findBySourceTypeAndSourceIdAndRewardType(sourceTypeName, sourceId, OrderRewardRecord.REWARD_TYPE_COMPLETION);
        if (existingOpt.isPresent()) {
            OrderRewardRecord existing = existingOpt.get();
            if (existing.getStatus() == OrderRewardStatus.GRANTED
                    || existing.getStatus() == OrderRewardStatus.EVALUATED_ZERO) {
                int currentBalance = readCurrentWalletBalance(buyer);
                return new RewardEvaluation(
                        canonical.sourceType(),
                        sourceId,
                        buyer.getId(),
                        buyer.getFullName(),
                        existing.getEligibleNetAmount(),
                        existing.getPointsAwarded(),
                        currentBalance,
                        existing.getStatus(),
                        true,
                        "Nguồn đơn đã được xét thưởng trước đó"
                );
            }
        }

        // Kiểm tra tương thích ngược cờ rewardProcessed cho đơn độc lập cũ
        if (canonical.legacyAlreadyProcessed()) {
            int currentBalance = readCurrentWalletBalance(buyer);
            return new RewardEvaluation(
                    canonical.sourceType(),
                    sourceId,
                    buyer.getId(),
                    buyer.getFullName(),
                    canonical.eligibleNetGoodsAmount(),
                    canonical.legacyPointsEarned(),
                    currentBalance,
                    canonical.legacyPointsEarned() > 0 ? OrderRewardStatus.GRANTED : OrderRewardStatus.EVALUATED_ZERO,
                    true,
                    "Đơn hàng đã được cộng thưởng từ phiên bản trước"
            );
        }

        Instant now = clock.instant();

        // 3. Xác minh chứng từ thu tiền hợp lệ (hoặc căn cứ đơn 0đ ưu đãi toàn bộ) (XU-05)
        Optional<PaymentEvidenceService.PaymentEvidence> evidenceOpt =
                paymentEvidenceService.findSettledEvidence(canonical.sourceType(), sourceId);
        if (evidenceOpt.isEmpty()) {
            OrderRewardRecord pendingRecord = existingOpt.orElseGet(() -> OrderRewardRecord.builder()
                    .sourceType(sourceTypeName)
                    .sourceId(sourceId)
                    .rewardType(OrderRewardRecord.REWARD_TYPE_COMPLETION)
                    .userId(buyer.getId())
                    .createdAt(now)
                    .build());
            pendingRecord.setEligibleNetAmount(canonical.eligibleNetGoodsAmount());
            pendingRecord.setPointsAwarded(0);
            pendingRecord.setStatus(OrderRewardStatus.PENDING_PAYMENT_EVIDENCE);
            pendingRecord.setNote("Chờ chứng từ thanh toán hợp lệ trước khi cộng xu");
            orderRewardRecordRepository.save(pendingRecord);

            return new RewardEvaluation(
                    canonical.sourceType(),
                    sourceId,
                    buyer.getId(),
                    buyer.getFullName(),
                    canonical.eligibleNetGoodsAmount(),
                    0,
                    readCurrentWalletBalance(buyer),
                    OrderRewardStatus.PENDING_PAYMENT_EVIDENCE,
                    false,
                    "Đơn đã hoàn tất nhưng chưa có chứng từ thu tiền hợp lệ"
            );
        }

        PaymentEvidenceService.PaymentEvidence evidence = evidenceOpt.get();
        BigDecimal effectiveNet = evidence.zeroAmountPromo()
                ? BigDecimal.ZERO
                : canonical.eligibleNetGoodsAmount();
        int earnedPoints = evidence.zeroAmountPromo() ? 0 : estimateRewardPoints(effectiveNet);

        // 4. Khóa User + PointWallet để chống race tạo ví trùng (XU-08) và ghi nguyên tử trong 1 transaction (XU-07)
        PointWallet wallet = pointWalletLockHelper.lockOrCreateWallet(buyer, clock);
        LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);

        Long txId = null;
        if (earnedPoints > 0) {
            int newBalance = (wallet.getBalance() != null ? wallet.getBalance() : 0) + earnedPoints;
            wallet.setBalance(newBalance);
            wallet.setUpdatedAt(nowUtc);
            wallet = pointWalletRepository.save(wallet);
            buyer.setPointWallet(wallet);

            PointTransaction savedTx = pointTransactionRepository.save(PointTransaction.builder()
                    .pointWallet(wallet)
                    .amount(earnedPoints)
                    .type(PointTransactionType.EARN)
                    .sourceType(sourceTypeName)
                    .sourceId(sourceId.toString())
                    .rewardType(OrderRewardRecord.REWARD_TYPE_COMPLETION)
                    .description("Thưởng hoàn thành đơn " + sourceTypeName + " #" + sourceId)
                    .createdAt(nowUtc)
                    .build());
            txId = savedTx.getId();
        }

        OrderRewardStatus finalStatus = earnedPoints > 0
                ? OrderRewardStatus.GRANTED
                : OrderRewardStatus.EVALUATED_ZERO;

        OrderRewardRecord record = existingOpt.orElseGet(() -> OrderRewardRecord.builder()
                .sourceType(sourceTypeName)
                .sourceId(sourceId)
                .rewardType(OrderRewardRecord.REWARD_TYPE_COMPLETION)
                .userId(buyer.getId())
                .createdAt(now)
                .build());
        record.setEligibleNetAmount(effectiveNet);
        record.setPointsAwarded(earnedPoints);
        record.setStatus(finalStatus);
        record.setPaymentId(evidence.paymentId());
        record.setPointTransactionId(txId);
        record.setProcessedAt(now);
        record.setNote(evidence.zeroAmountPromo()
                ? "Đơn ưu đãi toàn bộ 0đ - đã xét 0 xu"
                : (earnedPoints == 0 ? "Giá trị hàng sau giảm dưới 10.000đ - đã xét 0 xu" : "Đã cộng " + earnedPoints + " xu"));
        orderRewardRecordRepository.save(record);

        // Đồng bộ cờ legacy trên đơn độc lập để giữ tương thích các màn hình/API cũ
        syncLegacyRewardFlags(canonical, earnedPoints, now);

        log.info("Xét thưởng hoàn tất {}:{} cho user {}: net={}, points={}, status={}",
                sourceTypeName, sourceId, buyer.getId(), effectiveNet, earnedPoints, finalStatus);

        return new RewardEvaluation(
                canonical.sourceType(),
                sourceId,
                buyer.getId(),
                buyer.getFullName(),
                effectiveNet,
                earnedPoints,
                wallet.getBalance() != null ? wallet.getBalance() : 0,
                finalStatus,
                false,
                record.getNote()
        );
    }

    @Override
    @Transactional
    public RewardEvaluation completeCodAndReward(
            PaymentEvidenceService.SourceType requestedType,
            UUID requestedId,
            BigDecimal collectedAmount,
            User admin
    ) {
        CanonicalSource canonical = resolveAndLockCanonicalSource(requestedType, requestedId);
        paymentEvidenceService.recordCodCollection(canonical.sourceType(), canonical.sourceId(), collectedAmount, admin);
        return processCompletionReward(canonical.sourceType(), canonical.sourceId());
    }

    @Override
    @Transactional
    public RewardEvaluation retryRewardAfterPaymentSettled(PaymentEvidenceService.SourceType sourceType, UUID sourceId) {
        return processCompletionReward(sourceType, sourceId);
    }

    @Override
    public int estimateRewardPoints(BigDecimal eligibleNetGoodsAmount) {
        if (eligibleNetGoodsAmount == null || eligibleNetGoodsAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return eligibleNetGoodsAmount.divideToIntegralValue(REWARD_POINT_UNIT).intValue();
    }

    BigDecimal computeCheckoutEligibleNetGoods(
            CheckoutOrder checkout,
            List<CustomOrder> customOrders,
            List<Order> marketplaceOrders
    ) {
        BigDecimal sumRaw = BigDecimal.ZERO;
        BigDecimal sumChildDiscount = BigDecimal.ZERO;

        for (CustomOrder c : customOrders) {
            if (!"COMPLETED".equalsIgnoreCase(c.getStatus())) {
                continue; // Loại dòng CANCELLED hoặc chưa hoàn tất (XU-03)
            }
            BigDecimal lineSub = resolveCustomGoodsSubtotal(c);
            BigDecimal lineDisc = nonNegative(c.getDiscountAmount());
            sumRaw = sumRaw.add(lineSub);
            sumChildDiscount = sumChildDiscount.add(lineDisc);
        }

        for (Order o : marketplaceOrders) {
            if (!"COMPLETED".equalsIgnoreCase(o.getStatus())) {
                continue;
            }
            BigDecimal lineSub = resolveOrderGoodsSubtotal(o);
            BigDecimal lineDisc = nonNegative(o.getDiscountAmount());
            sumRaw = sumRaw.add(lineSub);
            sumChildDiscount = sumChildDiscount.add(lineDisc);
        }

        BigDecimal parentDiscount = nonNegative(checkout.getDiscountAmount());
        BigDecimal effectiveDiscount = sumChildDiscount.compareTo(BigDecimal.ZERO) > 0
                ? sumChildDiscount
                : parentDiscount;

        return sumRaw.subtract(effectiveDiscount).max(BigDecimal.ZERO);
    }

    BigDecimal resolveOrderGoodsSubtotal(Order order) {
        if (order == null) return BigDecimal.ZERO;
        if (order.getSubtotalAmount() != null) {
            return order.getSubtotalAmount().max(BigDecimal.ZERO);
        }
        BigDecimal total = nonNegative(order.getTotalAmount());
        BigDecimal ship = nonNegative(order.getShippingFee());
        BigDecimal shipDisc = nonNegative(order.getShippingDiscountAmount());
        BigDecimal netShip = ship.subtract(shipDisc).max(BigDecimal.ZERO);
        return total.subtract(netShip).max(BigDecimal.ZERO);
    }

    BigDecimal resolveCustomGoodsSubtotal(CustomOrder order) {
        if (order == null) return BigDecimal.ZERO;
        if (order.getSubtotalAmount() != null) {
            return order.getSubtotalAmount().max(BigDecimal.ZERO);
        }
        if (order.getQuotedPrice() != null && order.getQuotedPrice().compareTo(BigDecimal.ZERO) > 0) {
            return order.getQuotedPrice();
        }
        int qty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
        if (order.getUnitQuotedPrice() != null && order.getUnitQuotedPrice().compareTo(BigDecimal.ZERO) > 0) {
            return order.getUnitQuotedPrice().multiply(BigDecimal.valueOf(qty));
        }
        return BigDecimal.ZERO;
    }

    private CanonicalSource resolveAndLockCanonicalSource(
            PaymentEvidenceService.SourceType requestedType,
            UUID requestedId
    ) {
        return switch (requestedType) {
            case CHECKOUT_ORDER -> lockCheckoutSource(requestedId);
            case ORDER -> {
                Order order = orderRepository.findLockedById(requestedId)
                        .orElseThrow(() -> new ApiException(OrderErrorCode.ORDER_NOT_FOUND,
                                "Không tìm thấy đơn hàng ID: " + requestedId));
                if (order.getCheckoutOrder() != null) {
                    yield lockCheckoutSource(order.getCheckoutOrder().getId());
                }
                BigDecimal sub = resolveOrderGoodsSubtotal(order);
                BigDecimal net = sub.subtract(nonNegative(order.getDiscountAmount())).max(BigDecimal.ZERO);
                yield new CanonicalSource(
                        PaymentEvidenceService.SourceType.ORDER,
                        order.getId(),
                        order.getBuyer(),
                        order.getStatus(),
                        net,
                        Boolean.TRUE.equals(order.getRewardProcessed()),
                        order.getRewardPointsEarned() != null ? order.getRewardPointsEarned() : 0,
                        null,
                        order,
                        null
                );
            }
            case CUSTOM_ORDER -> {
                CustomOrder custom = customOrderRepository.findLockedById(requestedId)
                        .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND,
                                "Không tìm thấy yêu cầu in custom ID: " + requestedId));
                if (custom.getCheckoutOrder() != null) {
                    yield lockCheckoutSource(custom.getCheckoutOrder().getId());
                }
                BigDecimal sub = resolveCustomGoodsSubtotal(custom);
                BigDecimal net = sub.subtract(nonNegative(custom.getDiscountAmount())).max(BigDecimal.ZERO);
                yield new CanonicalSource(
                        PaymentEvidenceService.SourceType.CUSTOM_ORDER,
                        custom.getId(),
                        custom.getBuyer(),
                        custom.getStatus(),
                        net,
                        Boolean.TRUE.equals(custom.getRewardProcessed()),
                        custom.getRewardPointsEarned() != null ? custom.getRewardPointsEarned() : 0,
                        null,
                        null,
                        custom
                );
            }
        };
    }

    private CanonicalSource lockCheckoutSource(UUID checkoutId) {
        CheckoutOrder co = checkoutOrderRepository.findLockedById(checkoutId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy CheckoutOrder ID: " + checkoutId));
        List<CustomOrder> customs = customOrderRepository.findByCheckoutOrderId(checkoutId);
        List<Order> orders = orderRepository.findByCheckoutOrderId(checkoutId);
        BigDecimal net = computeCheckoutEligibleNetGoods(co, customs, orders);
        return new CanonicalSource(
                PaymentEvidenceService.SourceType.CHECKOUT_ORDER,
                co.getId(),
                co.getUser(),
                co.getStatus(),
                net,
                false,
                0,
                co,
                null,
                null
        );
    }

    private void syncLegacyRewardFlags(CanonicalSource canonical, int earnedPoints, Instant now) {
        if (canonical.standaloneOrder() != null) {
            Order o = canonical.standaloneOrder();
            o.setRewardProcessed(true);
            o.setRewardPointsEarned(earnedPoints);
            o.setUpdatedAt(now);
            orderRepository.save(o);
        } else if (canonical.standaloneCustom() != null) {
            CustomOrder c = canonical.standaloneCustom();
            c.setRewardProcessed(true);
            c.setRewardPointsEarned(earnedPoints);
            c.setUpdatedAt(now);
            customOrderRepository.save(c);
        }
    }

    private int readCurrentWalletBalance(User buyer) {
        return pointWalletRepository.findByUserId(buyer.getId())
                .map(w -> w.getBalance() != null ? w.getBalance() : 0)
                .orElseGet(() -> buyer.getRewardPoints() != null ? buyer.getRewardPoints() : 0);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : BigDecimal.ZERO;
    }

    private record CanonicalSource(
            PaymentEvidenceService.SourceType sourceType,
            UUID sourceId,
            User buyer,
            String status,
            BigDecimal eligibleNetGoodsAmount,
            boolean legacyAlreadyProcessed,
            int legacyPointsEarned,
            CheckoutOrder checkoutOrder,
            Order standaloneOrder,
            CustomOrder standaloneCustom
    ) {}
}
