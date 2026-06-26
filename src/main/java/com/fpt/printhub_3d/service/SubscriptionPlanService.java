package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanRequestDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanResponseDTO;
import com.fpt.printhub_3d.entity.Enumeration.SubscriptionType;

import java.util.UUID;

public interface SubscriptionPlanService {
    SubscriptionPlanResponseDTO createPlan(SubscriptionType type, SubscriptionPlanRequestDTO request);

    SubscriptionPlanResponseDTO updatePlan(SubscriptionType type, UUID id, SubscriptionPlanRequestDTO request);

    void deletePlan(SubscriptionType type, UUID id);
}
