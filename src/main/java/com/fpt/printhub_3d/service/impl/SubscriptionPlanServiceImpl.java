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

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .type(type)
                .name(request.name())
                .price(request.price())
                .benefits(request.benefits())
                .requiredPoints(type == SubscriptionType.CUSTOMER ? request.requiredPoints() : null)
                .isActive(true)
                .build();

        SubscriptionPlan savedPlan = subscriptionPlanRepository.save(plan);
        log.info("Tạo gói subscription [{}] loại [{}]", savedPlan.getId(), type);
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

        plan.setName(request.name());
        plan.setPrice(request.price());
        plan.setBenefits(request.benefits());
        plan.setRequiredPoints(type == SubscriptionType.CUSTOMER ? request.requiredPoints() : null);

        SubscriptionPlan savedPlan = subscriptionPlanRepository.save(plan);
        log.info("Cập nhật gói subscription [{}] loại [{}]", savedPlan.getId(), type);
        return toResponse(savedPlan);
    }

    @Override
    @Transactional
    public void deletePlan(SubscriptionType type, UUID id) {
        SubscriptionPlan plan = findPlanByIdAndType(id, type);
        subscriptionPlanRepository.delete(plan);
        log.info("Xóa gói subscription [{}] loại [{}]", id, type);
    }

    private SubscriptionPlan findPlanByIdAndType(UUID id, SubscriptionType type) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ApiException(SubscriptionErrorCode.PLAN_NOT_FOUND));
        if (plan.getType() != type) {
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
                .requiredPoints(plan.getRequiredPoints())
                .isActive(plan.getIsActive())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}
