package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.SubscriptionErrorCode;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanRequestDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanResponseDTO;
import com.fpt.printhub_3d.entity.Enumeration.SubscriptionType;
import com.fpt.printhub_3d.entity.SubscriptionPlan;
import com.fpt.printhub_3d.repository.SubscriptionPlanRepository;
import com.fpt.printhub_3d.service.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPlanServiceImpl implements SubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Override
    @Transactional
    public SubscriptionPlanResponseDTO createPlan(SubscriptionType type, SubscriptionPlanRequestDTO request) {
        if (subscriptionPlanRepository.existsByNameIgnoreCaseAndType(request.name(), type)) {
            throw new ApiException(SubscriptionErrorCode.DUPLICATE_PLAN_NAME);
        }

        validateAcquisitionPolicy(type, request);

        int durationDays = (request.durationDays() != null && request.durationDays() > 0) ? request.durationDays() : 30;
        boolean allowMoney = request.allowMoneyPurchase() != null ? request.allowMoneyPurchase() : true;
        boolean allowPoints = request.allowPointRedemption() != null ? request.allowPointRedemption() : (type == SubscriptionType.CUSTOMER);
        boolean allowGift = request.allowAdminGift() != null ? request.allowAdminGift() : true;

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .type(type)
                .name(request.name().trim())
                .price(allowMoney ? request.price() : null)
                .benefits(request.benefits().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .requiredPoints(allowPoints ? request.requiredPoints() : null)
                .durationDays(durationDays)
                .planVersion(1)
                .allowMoneyPurchase(allowMoney)
                .allowPointRedemption(allowPoints)
                .allowAdminGift(allowGift)
                .structuredBenefits(request.structuredBenefits())
                .isActive(true)
                .build();

        SubscriptionPlan savedPlan = subscriptionPlanRepository.save(plan);
        log.info("Tạo gói subscription [{}] loại [{}] thời hạn [{} ngày], v[{}]", savedPlan.getId(), type, durationDays, savedPlan.getPlanVersion());
        return toResponse(savedPlan);
    }

    @Override
    @Transactional
    public SubscriptionPlanResponseDTO updatePlan(SubscriptionType type, UUID id, SubscriptionPlanRequestDTO request) {
        SubscriptionPlan plan = findPlanByIdAndType(id, type);
        subscriptionPlanRepository.findByNameIgnoreCaseAndType(request.name(), type)
                .filter(existingPlan -> !existingPlan.getId().equals(id))
                .ifPresent(existingPlan -> {
                    throw new ApiException(SubscriptionErrorCode.DUPLICATE_PLAN_NAME);
                });

        // Optimistic locking check via planVersion
        if (request.planVersion() != null && plan.getPlanVersion() != null && !request.planVersion().equals(plan.getPlanVersion())) {
            log.warn("Xung đột phiên bản gói [{}]: client gửi v[{}], server hiện tại v[{}]", id, request.planVersion(), plan.getPlanVersion());
            throw new ApiException(SubscriptionErrorCode.PLAN_VERSION_CONFLICT);
        }

        validateAcquisitionPolicy(type, request);

        boolean allowMoney = request.allowMoneyPurchase() != null ? request.allowMoneyPurchase() : Boolean.TRUE.equals(plan.getAllowMoneyPurchase());
        boolean allowPoints = request.allowPointRedemption() != null ? request.allowPointRedemption() : Boolean.TRUE.equals(plan.getAllowPointRedemption());
        boolean allowGift = request.allowAdminGift() != null ? request.allowAdminGift() : Boolean.TRUE.equals(plan.getAllowAdminGift());

        plan.setName(request.name().trim());
        plan.setBenefits(request.benefits().trim());
        if (request.description() != null) {
            plan.setDescription(request.description().trim());
        }
        plan.setAllowMoneyPurchase(allowMoney);
        plan.setAllowPointRedemption(allowPoints);
        plan.setAllowAdminGift(allowGift);
        plan.setPrice(allowMoney ? request.price() : null);
        plan.setRequiredPoints(allowPoints ? request.requiredPoints() : null);

        if (request.durationDays() != null && request.durationDays() > 0) {
            plan.setDurationDays(request.durationDays());
        }
        if (request.structuredBenefits() != null) {
            plan.setStructuredBenefits(request.structuredBenefits());
        }

        int currentVer = plan.getPlanVersion() != null ? plan.getPlanVersion() : 1;
        plan.setPlanVersion(currentVer + 1);

        SubscriptionPlan savedPlan = subscriptionPlanRepository.save(plan);
        log.info("Cập nhật gói subscription [{}] loại [{}] lên v[{}]", savedPlan.getId(), type, savedPlan.getPlanVersion());
        return toResponse(savedPlan);
    }

    private void validateAcquisitionPolicy(SubscriptionType type, SubscriptionPlanRequestDTO request) {
        boolean allowMoney = request.allowMoneyPurchase() != null ? request.allowMoneyPurchase() : true;
        boolean allowPoints = request.allowPointRedemption() != null ? request.allowPointRedemption() : (type == SubscriptionType.CUSTOMER);
        boolean allowGift = request.allowAdminGift() != null ? request.allowAdminGift() : true;

        if (!allowMoney && !allowPoints && !allowGift) {
            throw new ApiException(SubscriptionErrorCode.NO_ACQUISITION_METHOD);
        }

        if (allowMoney) {
            if (request.price() == null || request.price().compareTo(java.math.BigDecimal.ZERO) < 0) {
                throw new ApiException(SubscriptionErrorCode.INVALID_PRICE);
            }
        }

        if (allowPoints) {
            if (request.requiredPoints() == null || request.requiredPoints() < 0) {
                throw new ApiException(SubscriptionErrorCode.INVALID_REQUIRED_POINTS);
            }
        }
    }

    @Override
    @Transactional
    public void deletePlan(SubscriptionType type, UUID id) {
        SubscriptionPlan plan = findPlanByIdAndType(id, type);
        plan.setIsActive(false);
        subscriptionPlanRepository.save(plan);
        log.info("Xóa gói subscription [{}] loại [{}]", id, type);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<SubscriptionPlanResponseDTO> getAllPlansForAdmin() {
        return subscriptionPlanRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private SubscriptionPlan findPlanByIdAndType(UUID id, SubscriptionType type) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ApiException(SubscriptionErrorCode.PLAN_NOT_FOUND));
        boolean isCustomerScope = (type == SubscriptionType.CUSTOMER || type == SubscriptionType.CUSTOMER_VIP)
                && (plan.getType() == SubscriptionType.CUSTOMER || plan.getType() == SubscriptionType.CUSTOMER_VIP);
        if (plan.getType() != type && !isCustomerScope) {
            throw new ApiException(SubscriptionErrorCode.PLAN_NOT_FOUND);
        }
        return plan;
    }

    private SubscriptionPlanResponseDTO toResponse(SubscriptionPlan plan) {
        return SubscriptionPlanResponseDTO.builder()
                .id(plan.getId())
                .type(plan.getType())
                .name(plan.getName())
                .price(plan.getPrice())
                .benefits(plan.getBenefits())
                .description(plan.getDescription())
                .requiredPoints(plan.getRequiredPoints())
                .durationDays(plan.getDurationDays() != null && plan.getDurationDays() > 0 ? plan.getDurationDays() : 30)
                .planVersion(plan.getPlanVersion() != null ? plan.getPlanVersion() : 1)
                .allowMoneyPurchase(plan.getAllowMoneyPurchase() != null ? plan.getAllowMoneyPurchase() : true)
                .allowPointRedemption(plan.getAllowPointRedemption() != null ? plan.getAllowPointRedemption() : true)
                .allowAdminGift(plan.getAllowAdminGift() != null ? plan.getAllowAdminGift() : true)
                .structuredBenefits(plan.getStructuredBenefits())
                .isActive(plan.getIsActive())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}
