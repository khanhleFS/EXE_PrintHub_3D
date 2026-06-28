package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.CommonErrorCode;
import com.fpt.printhub_3d.dto.subscription.GiftSubscriptionRequestDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanResponseDTO;
import com.fpt.printhub_3d.dto.subscription.UserSubscriptionResponseDTO;
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
import java.util.Collections;
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

    @Override
    public List<SubscriptionPlanResponseDTO> getAvailablePlansForUser(UUID userId) {
        log.info("Lấy danh sách gói phù hợp cho User [{}]", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng."));

        SubscriptionType planType;
        if (user.getRole() == UserRole.USER) {
            planType = SubscriptionType.CUSTOMER_VIP;
        } else if (user.getRole() == UserRole.MAKER) {
            planType = SubscriptionType.MAKER_MARKETING;
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
    @Transactional
    public UserSubscriptionResponseDTO redeemSubscription(UUID userId, UUID planId) {
        log.info("Người dùng [{}] yêu cầu đổi điểm lấy gói [{}]", userId, planId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng."));

        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy gói dịch vụ."));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Gói dịch vụ này hiện tại không hoạt động.");
        }

        if (plan.getType() == SubscriptionType.CUSTOMER_VIP && user.getRole() != UserRole.USER) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Gói VIP người mua chỉ dành cho tài khoản khách hàng.");
        }
        if (plan.getType() == SubscriptionType.MAKER_MARKETING && user.getRole() != UserRole.MAKER) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Gói Marketing người bán chỉ dành cho tài khoản Maker.");
        }

        Integer requiredPoints = plan.getRequiredPoints();
        if (requiredPoints == null || requiredPoints <= 0) {
            throw new ApiException(CommonErrorCode.BAD_REQUEST, "Gói dịch vụ này không hỗ trợ đổi bằng điểm thưởng.");
        }

        PointWallet wallet = pointWalletRepository.findById(userId).orElseGet(() -> {
            PointWallet newWallet = PointWallet.builder()
                    .userId(userId)
                    .user(user)
                    .balance(0)
                    .updatedAt(LocalDateTime.now())
                    .build();
            return pointWalletRepository.save(newWallet);
        });

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

        UserSubscription userSub = processSubscriptionActivation(user, plan);

        return toResponseDTO(userSub);
    }

    @Override
    @Transactional
    public UserSubscriptionResponseDTO giftSubscription(GiftSubscriptionRequestDTO request) {
        log.info("Admin thực hiện phát gói [{}] cho người dùng [{}] với lý do: {}", request.planId(), request.userId(), request.reason());

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng được tặng."));

        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.planId())
                .orElseThrow(() -> new ApiException(CommonErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy gói dịch vụ."));

        UserSubscription userSub = processSubscriptionActivation(user, plan);

        return toResponseDTO(userSub);
    }

    private UserSubscription processSubscriptionActivation(User user, SubscriptionPlan plan) {
        LocalDateTime now = LocalDateTime.now();
        int durationDays = 30; // Mặc định gói kéo dài 30 ngày

        Optional<UserSubscription> activeSubOpt = userSubscriptionRepository
                .findActiveSubscriptionByUserAndType(user, plan.getType());

        UserSubscription userSub;
        if (activeSubOpt.isPresent()) {
            UserSubscription activeSub = activeSubOpt.get();
            activeSub.setEndDate(activeSub.getEndDate().plusDays(durationDays));
            userSub = userSubscriptionRepository.save(activeSub);
            log.info("Đã gia hạn gói dịch vụ [{}] cho người dùng [{}]. Hạn mới: {}", plan.getName(), user.getId(), userSub.getEndDate());
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
