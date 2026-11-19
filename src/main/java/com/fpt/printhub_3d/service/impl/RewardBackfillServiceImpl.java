package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.order.RewardBackfillReportDTO;
import com.fpt.printhub_3d.dto.order.RewardBackfillReportDTO.Action;
import com.fpt.printhub_3d.dto.order.RewardBackfillReportDTO.BackfillItemResult;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.OrderRewardStatus;
import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.PaymentEvidenceService;
import com.fpt.printhub_3d.service.RewardBackfillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Đối soát & cộng bù xu cho đơn cũ đã hoàn tất (XU-09, E2E-04).
 * - Kiểm tra tổng xu đã ghi trên cả đơn cha (OrderRewardRecord) lẫn các đơn con (legacy rewardPointsEarned).
 * - Cộng phần chênh lệch thiếu bằng khóa riêng COMPLETION_BACKFILL để chạy lần 2 luôn cộng 0.
 * - Tuyệt đối KHÔNG gọi EmailOutboxService để không tái phát email biên nhận cũ (EM-11, E2E-04).
 */
@Slf4j
@Service
public class RewardBackfillServiceImpl implements RewardBackfillService {

    private final CheckoutOrderRepository checkoutOrderRepository;
    private final OrderRepository orderRepository;
    private final CustomOrderRepository customOrderRepository;
    private final OrderRewardRecordRepository orderRewardRecordRepository;
    private final PointWalletRepository pointWalletRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final PointWalletLockHelper pointWalletLockHelper;
    private final PaymentEvidenceService paymentEvidenceService;
    private final OrderRewardServiceImpl orderRewardService;
    private final Clock clock;

    @Autowired
    public RewardBackfillServiceImpl(
            CheckoutOrderRepository checkoutOrderRepository,
            OrderRepository orderRepository,
            CustomOrderRepository customOrderRepository,
            OrderRewardRecordRepository orderRewardRecordRepository,
            PointWalletRepository pointWalletRepository,
            PointTransactionRepository pointTransactionRepository,
            PointWalletLockHelper pointWalletLockHelper,
            PaymentEvidenceService paymentEvidenceService,
            OrderRewardServiceImpl orderRewardService,
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
        this.orderRewardService = orderRewardService;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    @Transactional
    public RewardBackfillReportDTO runBackfill(boolean dryRun) {
        Instant now = clock.instant();
        List<BackfillItemResult> items = new ArrayList<>();

        // 1. Quét toàn bộ CheckoutOrder đã COMPLETED
        List<CheckoutOrder> checkouts = checkoutOrderRepository.findAll();
        for (CheckoutOrder co : checkouts) {
            if (!"COMPLETED".equalsIgnoreCase(co.getStatus())) {
                continue;
            }
            items.add(evaluateCheckoutOrder(co, dryRun, now));
        }

        // 2. Quét toàn bộ Order độc lập đã COMPLETED (không thuộc CheckoutOrder)
        List<Order> orders = orderRepository.findAll();
        for (Order o : orders) {
            if (o.getCheckoutOrder() != null || !"COMPLETED".equalsIgnoreCase(o.getStatus())) {
                continue;
            }
            items.add(evaluateStandaloneOrder(o, dryRun, now));
        }

        // 3. Quét toàn bộ CustomOrder độc lập đã COMPLETED (không thuộc CheckoutOrder)
        List<CustomOrder> customs = customOrderRepository.findAll();
        for (CustomOrder c : customs) {
            if (c.getCheckoutOrder() != null || !"COMPLETED".equalsIgnoreCase(c.getStatus())) {
                continue;
            }
            items.add(evaluateStandaloneCustom(c, dryRun, now));
        }

        int adjusted = 0;
        int matched = 0;
        int reconcile = 0;
        int totalAdded = 0;

        for (BackfillItemResult item : items) {
            switch (item.action()) {
                case WOULD_GRANT, GRANTED -> {
                    adjusted++;
                    if (item.action() == Action.GRANTED) {
                        totalAdded += item.deltaPoints();
                    }
                }
                case NO_CHANGE -> matched++;
                case NEEDS_RECONCILIATION -> reconcile++;
            }
        }

        return RewardBackfillReportDTO.builder()
                .dryRun(dryRun)
                .executedAt(now)
                .scannedCount(items.size())
                .adjustedCount(adjusted)
                .alreadyMatchedCount(matched)
                .reconciliationRequiredCount(reconcile)
                .totalPointsAdded(totalAdded)
                .items(items)
                .build();
    }

    private BackfillItemResult evaluateCheckoutOrder(CheckoutOrder co, boolean dryRun, Instant now) {
        UUID coId = co.getId();
        List<CustomOrder> childCustoms = customOrderRepository.findByCheckoutOrderId(coId);
        List<Order> childOrders = orderRepository.findByCheckoutOrderId(coId);

        List<UUID> childIds = new ArrayList<>();
        int legacyChildPoints = 0;
        for (CustomOrder c : childCustoms) {
            childIds.add(c.getId());
            if (Boolean.TRUE.equals(c.getRewardProcessed()) && c.getRewardPointsEarned() != null) {
                legacyChildPoints += c.getRewardPointsEarned();
            }
        }
        for (Order o : childOrders) {
            childIds.add(o.getId());
            if (Boolean.TRUE.equals(o.getRewardProcessed()) && o.getRewardPointsEarned() != null) {
                legacyChildPoints += o.getRewardPointsEarned();
            }
        }

        List<OrderRewardRecord> parentRecords = orderRewardRecordRepository
                .findBySourceTypeAndSourceId(PaymentEvidenceService.SourceType.CHECKOUT_ORDER.name(), coId);
        int parentRecordedPoints = parentRecords.stream()
                .filter(r -> r.getStatus() == OrderRewardStatus.GRANTED)
                .mapToInt(r -> r.getPointsAwarded() != null ? r.getPointsAwarded() : 0)
                .sum();

        int alreadyGranted = parentRecordedPoints + legacyChildPoints;
        BigDecimal netGoods = orderRewardService.computeCheckoutEligibleNetGoods(co, childCustoms, childOrders);

        return finalizeEvaluation(
                PaymentEvidenceService.SourceType.CHECKOUT_ORDER,
                coId,
                co.getUser(),
                netGoods,
                alreadyGranted,
                childIds,
                dryRun,
                now
        );
    }

    private BackfillItemResult evaluateStandaloneOrder(Order order, boolean dryRun, Instant now) {
        UUID id = order.getId();
        List<OrderRewardRecord> records = orderRewardRecordRepository
                .findBySourceTypeAndSourceId(PaymentEvidenceService.SourceType.ORDER.name(), id);
        int recordedPoints = records.stream()
                .filter(r -> r.getStatus() == OrderRewardStatus.GRANTED)
                .mapToInt(r -> r.getPointsAwarded() != null ? r.getPointsAwarded() : 0)
                .sum();
        int legacyPoints = (recordedPoints == 0 && Boolean.TRUE.equals(order.getRewardProcessed())
                && order.getRewardPointsEarned() != null) ? order.getRewardPointsEarned() : 0;

        BigDecimal sub = orderRewardService.resolveOrderGoodsSubtotal(order);
        BigDecimal disc = order.getDiscountAmount() != null && order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                ? order.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal netGoods = sub.subtract(disc).max(BigDecimal.ZERO);

        return finalizeEvaluation(
                PaymentEvidenceService.SourceType.ORDER,
                id,
                order.getBuyer(),
                netGoods,
                recordedPoints + legacyPoints,
                List.of(),
                dryRun,
                now
        );
    }

    private BackfillItemResult evaluateStandaloneCustom(CustomOrder custom, boolean dryRun, Instant now) {
        UUID id = custom.getId();
        List<OrderRewardRecord> records = orderRewardRecordRepository
                .findBySourceTypeAndSourceId(PaymentEvidenceService.SourceType.CUSTOM_ORDER.name(), id);
        int recordedPoints = records.stream()
                .filter(r -> r.getStatus() == OrderRewardStatus.GRANTED)
                .mapToInt(r -> r.getPointsAwarded() != null ? r.getPointsAwarded() : 0)
                .sum();
        int legacyPoints = (recordedPoints == 0 && Boolean.TRUE.equals(custom.getRewardProcessed())
                && custom.getRewardPointsEarned() != null) ? custom.getRewardPointsEarned() : 0;

        BigDecimal sub = orderRewardService.resolveCustomGoodsSubtotal(custom);
        BigDecimal disc = custom.getDiscountAmount() != null && custom.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                ? custom.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal netGoods = sub.subtract(disc).max(BigDecimal.ZERO);

        return finalizeEvaluation(
                PaymentEvidenceService.SourceType.CUSTOM_ORDER,
                id,
                custom.getBuyer(),
                netGoods,
                recordedPoints + legacyPoints,
                List.of(),
                dryRun,
                now
        );
    }

    private BackfillItemResult finalizeEvaluation(
            PaymentEvidenceService.SourceType sourceType,
            UUID sourceId,
            User buyer,
            BigDecimal netGoods,
            int alreadyGranted,
            List<UUID> childIds,
            boolean dryRun,
            Instant now
    ) {
        UUID userId = buyer != null ? buyer.getId() : null;
        Optional<PaymentEvidenceService.PaymentEvidence> evidenceOpt =
                paymentEvidenceService.findSettledEvidence(sourceType, sourceId);

        if (evidenceOpt.isEmpty()) {
            return BackfillItemResult.builder()
                    .sourceType(sourceType.name())
                    .sourceId(sourceId)
                    .userId(userId)
                    .eligibleNetAmount(netGoods)
                    .expectedPoints(0)
                    .alreadyGrantedPoints(alreadyGranted)
                    .deltaPoints(0)
                    .action(Action.NEEDS_RECONCILIATION)
                    .reason("Thiếu chứng từ thanh toán hợp lệ, đưa vào danh sách chờ đối soát")
                    .inspectedChildIds(childIds)
                    .build();
        }

        PaymentEvidenceService.PaymentEvidence evidence = evidenceOpt.get();
        int expectedPoints = evidence.zeroAmountPromo() ? 0 : orderRewardService.estimateRewardPoints(netGoods);

        if (alreadyGranted > expectedPoints) {
            return BackfillItemResult.builder()
                    .sourceType(sourceType.name())
                    .sourceId(sourceId)
                    .userId(userId)
                    .eligibleNetAmount(netGoods)
                    .expectedPoints(expectedPoints)
                    .alreadyGrantedPoints(alreadyGranted)
                    .deltaPoints(0)
                    .action(Action.NEEDS_RECONCILIATION)
                    .reason("Số xu đã ghi (" + alreadyGranted + ") vượt mức kỳ vọng (" + expectedPoints + "), cần đối soát thủ công")
                    .inspectedChildIds(childIds)
                    .build();
        }

        int delta = expectedPoints - alreadyGranted;
        if (delta == 0) {
            return BackfillItemResult.builder()
                    .sourceType(sourceType.name())
                    .sourceId(sourceId)
                    .userId(userId)
                    .eligibleNetAmount(netGoods)
                    .expectedPoints(expectedPoints)
                    .alreadyGrantedPoints(alreadyGranted)
                    .deltaPoints(0)
                    .action(Action.NO_CHANGE)
                    .reason("Số xu đã khớp, không cần cộng bù")
                    .inspectedChildIds(childIds)
                    .build();
        }

        // Kiểm tra khóa điều chỉnh COMPLETION_BACKFILL chống chạy đúp
        Optional<OrderRewardRecord> existingBackfill = orderRewardRecordRepository
                .findBySourceTypeAndSourceIdAndRewardType(
                        sourceType.name(), sourceId, OrderRewardRecord.REWARD_TYPE_BACKFILL);
        if (existingBackfill.isPresent()) {
            return BackfillItemResult.builder()
                    .sourceType(sourceType.name())
                    .sourceId(sourceId)
                    .userId(userId)
                    .eligibleNetAmount(netGoods)
                    .expectedPoints(expectedPoints)
                    .alreadyGrantedPoints(alreadyGranted)
                    .deltaPoints(0)
                    .action(Action.NO_CHANGE)
                    .reason("Nguồn đơn đã có bản ghi cộng bù COMPLETION_BACKFILL trước đó")
                    .inspectedChildIds(childIds)
                    .build();
        }

        if (dryRun) {
            return BackfillItemResult.builder()
                    .sourceType(sourceType.name())
                    .sourceId(sourceId)
                    .userId(userId)
                    .eligibleNetAmount(netGoods)
                    .expectedPoints(expectedPoints)
                    .alreadyGrantedPoints(alreadyGranted)
                    .deltaPoints(delta)
                    .action(Action.WOULD_GRANT)
                    .reason("Đề xuất cộng bù +" + delta + " xu (kỳ vọng " + expectedPoints + ", đã ghi " + alreadyGranted + ")")
                    .inspectedChildIds(childIds)
                    .build();
        }

        if (buyer == null) {
            return BackfillItemResult.builder()
                    .sourceType(sourceType.name())
                    .sourceId(sourceId)
                    .userId(null)
                    .eligibleNetAmount(netGoods)
                    .expectedPoints(expectedPoints)
                    .alreadyGrantedPoints(alreadyGranted)
                    .deltaPoints(0)
                    .action(Action.NEEDS_RECONCILIATION)
                    .reason("Không xác định được tài khoản khách hàng để cộng bù")
                    .inspectedChildIds(childIds)
                    .build();
        }

        // Thực thi cộng bù nguyên tử: khóa ví + ghi bút toán + ghi khóa điều chỉnh COMPLETION_BACKFILL
        PointWallet wallet = pointWalletLockHelper.lockOrCreateWallet(buyer, clock);
        LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        wallet.setBalance((wallet.getBalance() != null ? wallet.getBalance() : 0) + delta);
        wallet.setUpdatedAt(nowUtc);
        wallet = pointWalletRepository.save(wallet);
        buyer.setPointWallet(wallet);

        PointTransaction tx = pointTransactionRepository.save(PointTransaction.builder()
                .pointWallet(wallet)
                .amount(delta)
                .type(PointTransactionType.EARN)
                .sourceType(sourceType.name())
                .sourceId(sourceId.toString())
                .rewardType(OrderRewardRecord.REWARD_TYPE_BACKFILL)
                .description("Cộng bù điểm thưởng hoàn tất đơn " + sourceType.name() + " #" + sourceId)
                .createdAt(nowUtc)
                .build());

        orderRewardRecordRepository.save(OrderRewardRecord.builder()
                .sourceType(sourceType.name())
                .sourceId(sourceId)
                .rewardType(OrderRewardRecord.REWARD_TYPE_BACKFILL)
                .userId(buyer.getId())
                .eligibleNetAmount(netGoods)
                .pointsAwarded(delta)
                .status(OrderRewardStatus.GRANTED)
                .paymentId(evidence.paymentId())
                .pointTransactionId(tx.getId())
                .note("Cộng bù +" + delta + " xu (kỳ vọng " + expectedPoints + ", đã ghi " + alreadyGranted + ")")
                .createdAt(now)
                .processedAt(now)
                .build());

        log.info("Cộng bù +{} xu cho {}:{} (user={})", delta, sourceType, sourceId, buyer.getId());

        return BackfillItemResult.builder()
                .sourceType(sourceType.name())
                .sourceId(sourceId)
                .userId(buyer.getId())
                .eligibleNetAmount(netGoods)
                .expectedPoints(expectedPoints)
                .alreadyGrantedPoints(alreadyGranted)
                .deltaPoints(delta)
                .action(Action.GRANTED)
                .reason("Đã cộng bù +" + delta + " xu thành công")
                .inspectedChildIds(childIds)
                .build();
    }
}
