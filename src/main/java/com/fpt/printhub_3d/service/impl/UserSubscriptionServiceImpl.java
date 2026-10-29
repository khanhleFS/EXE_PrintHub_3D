package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.common.exception.SubscriptionErrorCode;
import com.fpt.printhub_3d.dto.subscription.GiftSubscriptionRequestDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanResponseDTO;
import com.fpt.printhub_3d.dto.subscription.UserSubscriptionResponseDTO;
import com.fpt.printhub_3d.dto.subscription.UserSubscriptionSummaryDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import com.fpt.printhub_3d.entity.Enumeration.SubscriptionType;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSubscriptionServiceImpl implements UserSubscriptionService {

    private final UserRepository userRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final PointWalletRepository pointWalletRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final SubscriptionCommandRecordRepository subscriptionCommandRecordRepository;

    @Transactional
    @Override
    public List<SubscriptionPlanResponseDTO> getAvailablePlansForUser(UUID userId) {
        log.info("Lấy danh sách gói phù hợp cho User [{}]", userId);

        User user = userRepository.findLockedById(userId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng."));

        if (user.getRole() != UserRole.USER && user.getRole() != UserRole.ADMIN) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Loại người dùng không hợp lệ.");
        }

        return subscriptionPlanRepository.findByTypeInAndIsActiveTrue(List.of(SubscriptionType.CUSTOMER, SubscriptionType.CUSTOMER_VIP))
                .stream()
                .map(plan -> SubscriptionPlanResponseDTO.builder()
                        .id(plan.getId())
                        .type(plan.getType())
                        .name(plan.getName())
                        .price(plan.getPrice())
                        .benefits(plan.getBenefits())
                        .requiredPoints(plan.getRequiredPoints())
                        .durationDays(plan.getDurationDays() != null && plan.getDurationDays() > 0 ? plan.getDurationDays() : 30)
                        .isActive(plan.getIsActive())
                        .createdAt(plan.getCreatedAt())
                        .updatedAt(plan.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserSubscriptionSummaryDTO getUserSubscriptionSummary(UUID userId) {
        log.info("Lấy thông tin gói hội viên và điểm thưởng cho User [{}]", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng."));

        List<UserSubscriptionResponseDTO> activeSubs = userSubscriptionRepository
                .findActiveSubscriptionsByUser(user)
                .stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());

        return UserSubscriptionSummaryDTO.builder()
                .points(user.getRewardPoints())
                .subscriptions(activeSubs)
                .build();
    }

    @Override
    @Transactional
    public UserSubscriptionResponseDTO redeemSubscription(UUID userId, UUID planId) {
        return redeemSubscription(userId, planId, null);
    }

    @Override
    @Transactional
    public UserSubscriptionResponseDTO redeemSubscription(UUID userId, UUID planId, String idempotencyKey) {
        log.info("Người dùng [{}] yêu cầu đổi điểm lấy gói [{}], idempotencyKey: [{}]", userId, planId, idempotencyKey);

        // 1. Kiểm tra Idempotency key (nếu có)
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<SubscriptionCommandRecord> existingRecordOpt =
                    subscriptionCommandRecordRepository.findByRequestId(idempotencyKey.trim());
            if (existingRecordOpt.isPresent()) {
                SubscriptionCommandRecord record = existingRecordOpt.get();
                if (record.getUserId().equals(userId) && record.getPlanId().equals(planId)) {
                    log.info("Idempotency key [{}] cho đổi điểm đã được xử lý. Trả về kết quả trước đó.", idempotencyKey);
                    UserSubscription sub = userSubscriptionRepository.findById(record.getSubscriptionId())
                            .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy gói đã đổi."));
                    return toResponseDTO(sub);
                } else {
                    throw new ApiException(SubscriptionErrorCode.IDEMPOTENCY_CONFLICT,
                            "Mã idempotencyKey [" + idempotencyKey + "] đã được sử dụng cho một yêu cầu khác.");
                }
            }
        }

        // 2. Kiểm tra User
        User user = userRepository.findLockedById(userId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng."));

        if (!Boolean.TRUE.equals(user.getIsActive()) || user.getRole() != UserRole.USER) {
            throw new ApiException(SubscriptionErrorCode.INVALID_RECIPIENT, "Tài khoản người dùng không hợp lệ hoặc đã bị khóa.");
        }

        // 3. Kiểm tra Gói dịch vụ
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ApiException(SubscriptionErrorCode.PLAN_NOT_FOUND, "Không tìm thấy gói dịch vụ."));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new ApiException(SubscriptionErrorCode.PLAN_INACTIVE, "Gói dịch vụ này hiện tại không hoạt động.");
        }

        if (plan.getType() != SubscriptionType.CUSTOMER && plan.getType() != SubscriptionType.CUSTOMER_VIP) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Gói VIP người mua chỉ dành cho tài khoản khách hàng.");
        }

        Integer requiredPoints = plan.getRequiredPoints();
        if (requiredPoints == null || requiredPoints <= 0) {
            throw new ApiException(SubscriptionErrorCode.POINT_REDEMPTION_NOT_SUPPORTED, "Gói dịch vụ này không hỗ trợ đổi bằng điểm thưởng.");
        }

        // 4. KIỂM TRA XUNG ĐỘT GÓI TRƯỚC KHI TRỪ ĐIỂM (BẢO VỆ ĐIỂM USER)
        List<UserSubscription> activeSubs = userSubscriptionRepository.findActiveSubscriptionsByUser(user);
        for (UserSubscription activeSub : activeSubs) {
            if (activeSub.getPlan() != null && !activeSub.getPlan().getId().equals(plan.getId())) {
                log.warn("User [{}] đang có gói [{}] còn hiệu lực, không thể đổi sang gói [{}]",
                        userId, activeSub.getPlan().getName(), plan.getName());
                throw new ApiException(SubscriptionErrorCode.ACTIVE_PLAN_CONFLICT,
                        "ACTIVE_PLAN_CONFLICT: Người dùng đang có gói hội viên khác còn hiệu lực.");
            }
        }

        // 5. Khóa ví điểm an toàn và kiểm tra số dư
        PointWallet wallet = pointWalletRepository.findByUserIdForUpdate(userId).or(() -> pointWalletRepository.findById(userId)).orElseGet(() -> {
            PointWallet newWallet = PointWallet.builder()
                    .userId(userId)
                    .user(user)
                    .balance(0)
                    .isNew(true)
                    .updatedAt(LocalDateTime.now())
                    .build();
            user.setPointWallet(newWallet);
            return pointWalletRepository.save(newWallet);
        });
        user.setPointWallet(wallet);

        if (wallet.getBalance() < requiredPoints) {
            throw new ApiException(SubscriptionErrorCode.INSUFFICIENT_POINTS,
                    "Số dư điểm thưởng không đủ. Cần: " + requiredPoints + ", Hiện có: " + wallet.getBalance());
        }

        // 6. Trừ điểm ví
        wallet.setBalance(wallet.getBalance() - requiredPoints);
        wallet.setUpdatedAt(LocalDateTime.now());
        pointWalletRepository.save(wallet);

        // 7. Ghi sổ cái PointTransaction REDEEM
        PointTransaction transaction = PointTransaction.builder()
                .pointWallet(wallet)
                .amount(-requiredPoints)
                .type(PointTransactionType.REDEEM)
                .description("Đổi điểm nhận gói hội viên: " + plan.getName())
                .createdAt(LocalDateTime.now())
                .build();
        pointTransactionRepository.save(transaction);

        // 8. Kích hoạt hoặc gia hạn gói
        UserSubscription userSub = processSubscriptionActivation(user, plan);

        // 9. Lưu Idempotency record nếu có
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            SubscriptionCommandRecord record = SubscriptionCommandRecord.builder()
                    .requestId(idempotencyKey.trim())
                    .userId(userId)
                    .planId(planId)
                    .subscriptionId(userSub.getId())
                    .commandType("REDEEM")
                    .createdAt(LocalDateTime.now())
                    .build();
            subscriptionCommandRecordRepository.save(record);
        }

        return toResponseDTO(userSub);
    }

    @Override
    @Transactional
    public UserSubscriptionResponseDTO giftSubscription(GiftSubscriptionRequestDTO request) {
        log.info("Admin thực hiện phát gói [{}] cho người dùng [{}] với lý do: {}, requestId: [{}]",
                request.planId(), request.userId(), request.reason(), request.requestId());

        // 1. Kiểm tra Idempotency key (nếu có)
        if (request.requestId() != null && !request.requestId().isBlank()) {
            Optional<SubscriptionCommandRecord> existingRecordOpt =
                    subscriptionCommandRecordRepository.findByRequestId(request.requestId().trim());
            if (existingRecordOpt.isPresent()) {
                SubscriptionCommandRecord record = existingRecordOpt.get();
                if (record.getUserId().equals(request.userId()) && record.getPlanId().equals(request.planId())) {
                    log.info("RequestId [{}] đã được xử lý trước đó. Trả về kết quả trước đó.", request.requestId());
                    UserSubscription sub = userSubscriptionRepository.findById(record.getSubscriptionId())
                            .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy gói đã cấp."));
                    return toResponseDTO(sub);
                } else {
                    throw new ApiException(SubscriptionErrorCode.IDEMPOTENCY_CONFLICT,
                            "Mã requestId [" + request.requestId() + "] đã được sử dụng cho một yêu cầu khác.");
                }
            }
        }

        // 2. Kiểm tra User
        User user = userRepository.findLockedById(request.userId())
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng được tặng."));

        if (!Boolean.TRUE.equals(user.getIsActive()) || user.getRole() != UserRole.USER) {
            throw new ApiException(SubscriptionErrorCode.INVALID_RECIPIENT, "Tài khoản người nhận không hợp lệ hoặc đã bị khóa.");
        }

        // 3. Kiểm tra Gói dịch vụ
        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.planId())
                .orElseThrow(() -> new ApiException(SubscriptionErrorCode.PLAN_NOT_FOUND, "Không tìm thấy gói dịch vụ."));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new ApiException(SubscriptionErrorCode.PLAN_INACTIVE, "Gói dịch vụ này hiện tại không hoạt động.");
        }

        if (plan.getType() != SubscriptionType.CUSTOMER && plan.getType() != SubscriptionType.CUSTOMER_VIP) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Gói được tặng không thuộc nhóm khách hàng.");
        }

        // 4. Kích hoạt hoặc gia hạn gói
        UserSubscription userSub = processSubscriptionActivation(user, plan);

        // 5. Lưu Idempotency record nếu có
        if (request.requestId() != null && !request.requestId().isBlank()) {
            SubscriptionCommandRecord record = SubscriptionCommandRecord.builder()
                    .requestId(request.requestId().trim())
                    .userId(user.getId())
                    .planId(plan.getId())
                    .subscriptionId(userSub.getId())
                    .commandType("GIFT")
                    .createdAt(LocalDateTime.now())
                    .build();
            subscriptionCommandRecordRepository.save(record);
        }

        return toResponseDTO(userSub);
    }

    private UserSubscription processSubscriptionActivation(User user, SubscriptionPlan plan) {
        LocalDateTime now = LocalDateTime.now();
        int durationDays = (plan.getDurationDays() != null && plan.getDurationDays() > 0) ? plan.getDurationDays() : 30;

        List<UserSubscription> activeSubs = userSubscriptionRepository.findActiveSubscriptionsByUser(user);

        UserSubscription userSub;
        if (!activeSubs.isEmpty()) {
            Optional<UserSubscription> samePlanSubOpt = activeSubs.stream()
                    .filter(sub -> sub.getPlan() != null && sub.getPlan().getId().equals(plan.getId()))
                    .findFirst();

            if (samePlanSubOpt.isPresent()) {
                UserSubscription activeSub = samePlanSubOpt.get();
                activeSub.setEndDate(activeSub.getEndDate().plusDays(durationDays));
                userSub = userSubscriptionRepository.save(activeSub);
                log.info("Đã gia hạn gói dịch vụ [{}] cho người dùng [{}]. Hạn mới: {}", plan.getName(), user.getId(), userSub.getEndDate());
            } else {
                log.warn("Xung đột gói: User [{}] đang có gói khác còn hiệu lực, không thể cấp gói [{}]", user.getId(), plan.getName());
                throw new ApiException(SubscriptionErrorCode.ACTIVE_PLAN_CONFLICT,
                        "ACTIVE_PLAN_CONFLICT: Người dùng đang có gói hội viên khác còn hiệu lực.");
            }
        } else {
            userSub = UserSubscription.builder()
                    .user(user)
                    .plan(plan)
                    .startDate(now)
                    .endDate(now.plusDays(durationDays))
                    .isActive(true)
                    .build();
            userSub = userSubscriptionRepository.save(userSub);
            log.info("Đăng ký mới gói dịch vụ [{}] cho người dùng [{}]. Hạn đến: {}", plan.getName(), user.getId(), userSub.getEndDate());
        }
        return userSub;
    }

    private UserSubscriptionResponseDTO toResponseDTO(UserSubscription userSub) {
        return UserSubscriptionResponseDTO.builder()
                .subscriptionId(userSub.getId())
                .userId(userSub.getUser().getId())
                .username(userSub.getUser().getUsername())
                .planId(userSub.getPlan().getId())
                .planName(userSub.getPlan().getName())
                .planType(userSub.getPlan().getType())
                .startDate(userSub.getStartDate())
                .endDate(userSub.getEndDate())
                .isActive(userSub.getIsActive())
                .build();
    }
}
