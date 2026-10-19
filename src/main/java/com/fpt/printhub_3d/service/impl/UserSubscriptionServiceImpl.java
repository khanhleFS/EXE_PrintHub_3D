package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
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

import java.math.BigDecimal;
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

    @Transactional
    @Override
    public List<SubscriptionPlanResponseDTO> getAvailablePlansForUser(UUID userId) {
        log.info("Lấy danh sách gói phù hợp cho User [{}]", userId);

        User user = userRepository.findLockedById(userId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng."));

        SubscriptionType planType;
        if (user.getRole() == UserRole.USER || user.getRole() == UserRole.ADMIN) {
            planType = SubscriptionType.CUSTOMER;
        } else {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Loại người dùng không hợp lệ.");
        }

        return subscriptionPlanRepository.findByTypeAndIsActiveTrue(planType)
                .stream()
                .map(plan -> SubscriptionPlanResponseDTO.builder()
                        .id(plan.getId())
                        .type(plan.getType())
                        .name(plan.getName())
                        .price(plan.getPrice())
                        .benefits(plan.getBenefits())
                        .requiredPoints(plan.getRequiredPoints())
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
        log.info("Người dùng [{}] yêu cầu đổi điểm lấy gói [{}]", userId, planId);

        User user = userRepository.findLockedById(userId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng."));

        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy gói dịch vụ."));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Gói dịch vụ này hiện tại không hoạt động.");
        }

        if ((plan.getType() != SubscriptionType.CUSTOMER && plan.getType() != SubscriptionType.CUSTOMER_VIP) || user.getRole() != UserRole.USER) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Gói VIP người mua chỉ dành cho tài khoản khách hàng.");
        }

        Integer requiredPoints = plan.getRequiredPoints();
        if (requiredPoints == null || requiredPoints <= 0) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Gói dịch vụ này không hỗ trợ đổi bằng điểm thưởng.");
        }

        // P1.4 / SUB-03: Kiểm tra tính hợp lệ trước khi trừ điểm ví
        validateSubscriptionEligibility(user, plan);

        PointWallet wallet = pointWalletRepository.findById(userId).orElseGet(() -> {
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
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Số dư điểm thưởng không đủ. Cần: " + requiredPoints + ", Hiện có: " + wallet.getBalance());
        }

        wallet.setBalance(wallet.getBalance() - requiredPoints);
        wallet.setUpdatedAt(LocalDateTime.now());
        pointWalletRepository.save(wallet);

        PointTransaction transaction = PointTransaction.builder()
                .pointWallet(wallet)
                .amount(-requiredPoints)
                .type(PointTransactionType.REDEEM)
                .description("Đổi điểm nhận gói hội viên: " + plan.getName())
                .createdAt(LocalDateTime.now())
                .build();
        pointTransactionRepository.save(transaction);

        UserSubscription userSub = processSubscriptionActivation(user, plan, "POINTS_REDEEM");

        return toResponseDTO(userSub);
    }

    @Override
    @Transactional
    public UserSubscriptionResponseDTO giftSubscription(GiftSubscriptionRequestDTO request) {
        log.info("Admin thực hiện phát gói [{}] cho người dùng [{}] với lý do: {}", request.planId(), request.userId(), request.reason());

        User user = userRepository.findLockedById(request.userId())
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng được tặng."));

        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.planId())
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy gói dịch vụ."));

        if (!Boolean.TRUE.equals(plan.getIsActive()) || user.getRole() != UserRole.USER
                || (plan.getType() != SubscriptionType.CUSTOMER && plan.getType() != SubscriptionType.CUSTOMER_VIP)) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Gói hoặc tài khoản không hợp lệ.");
        }
        UserSubscription userSub = processSubscriptionActivation(user, plan, "ADMIN_GIFT");

        return toResponseDTO(userSub);
    }

    private void validateSubscriptionEligibility(User user, SubscriptionPlan plan) {
        LocalDateTime now = LocalDateTime.now();
        List<UserSubscription> existingSubs = userSubscriptionRepository.findAllActiveAndFutureSubscriptionsByUser(user);
        if (existingSubs == null || existingSubs.isEmpty()) {
            existingSubs = userSubscriptionRepository.findActiveSubscriptionsByUser(user);
        }
        if (existingSubs != null) {
            for (UserSubscription existingSub : existingSubs) {
                if (existingSub.getPlan() != null && !existingSub.getPlan().getId().equals(plan.getId())) {
                    if (existingSub.getEndDate() != null && existingSub.getEndDate().isAfter(now)) {
                        String planName = existingSub.getPlan().getName() != null ? existingSub.getPlan().getName() : "khác";
                        log.warn("R2-04 SUB-03: Chặn đổi gói từ [{}] sang [{}]. Người dùng {} vẫn còn kỳ hiệu lực đến {}.",
                                planName, plan.getName(), user.getId(), existingSub.getEndDate());
                        throw new ApiException(CommonErrorCode.BAD_REQUEST,
                                String.format("Bạn đang có gói hội viên '%s' còn hiệu lực đến %s. Hệ thống chưa hỗ trợ chuyển đổi gói khi kỳ cũ chưa kết thúc. Vui lòng sử dụng hết thời hạn trước khi đăng ký gói mới.",
                                        planName, existingSub.getEndDate()));
                    }
                }
            }
        }
    }

    private UserSubscription processSubscriptionActivation(User user, SubscriptionPlan plan, String activationSource) {
        LocalDateTime now = LocalDateTime.now();
        int durationDays = (plan.getDurationDays() != null && plan.getDurationDays() > 0) ? plan.getDurationDays() : 30;
        boolean isFreeShip = Boolean.TRUE.equals(plan.getFreeShipping());
        BigDecimal shippingDiscount = (plan.getShippingDiscountAmount() != null && plan.getShippingDiscountAmount().compareTo(BigDecimal.ZERO) >= 0)
                ? plan.getShippingDiscountAmount()
                : BigDecimal.ZERO;

        // P1.4 / SUB-03: Đổi gói không âm thầm làm mất kỳ đã mua trước.
        validateSubscriptionEligibility(user, plan);

        // Gia hạn cùng gói: nối tiếp kỳ cuối đã mua dưới khóa User
        Optional<UserSubscription> latestSubOpt = userSubscriptionRepository
                .findTopByUserIdAndPlanIdOrderByEndDateDesc(user.getId(), plan.getId());

        LocalDateTime startDate = now;
        if (latestSubOpt.isPresent()) {
            UserSubscription latest = latestSubOpt.get();
            if (latest.getEndDate() != null && latest.getEndDate().isAfter(now)) {
                startDate = latest.getEndDate();
            }
        }
        LocalDateTime endDate = startDate.plusDays(durationDays);

        UserSubscription userSub = UserSubscription.builder()
                .user(user)
                .plan(plan)
                .startDate(startDate)
                .endDate(endDate)
                .isActive(true)
                .durationDays(durationDays)
                .appliedPrice(plan.getPrice())
                .appliedRequiredPoints(plan.getRequiredPoints())
                .appliedFreeShipping(isFreeShip)
                .appliedShippingDiscountAmount(shippingDiscount)
                .activationSource(activationSource)
                .build();
        userSub = userSubscriptionRepository.save(userSub);
        log.info("R2-04: Đã kích hoạt/gia hạn kỳ hội viên [{}] cho user [{}]: {} -> {}. Nguồn: {}",
                plan.getName(), user.getId(), startDate, endDate, activationSource);
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
