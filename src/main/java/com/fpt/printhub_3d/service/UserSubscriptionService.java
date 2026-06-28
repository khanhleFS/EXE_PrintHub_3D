package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.subscription.GiftSubscriptionRequestDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanResponseDTO;
import com.fpt.printhub_3d.dto.subscription.UserSubscriptionResponseDTO;

import java.util.List;
import java.util.UUID;

public interface UserSubscriptionService {
    List<SubscriptionPlanResponseDTO> getAvailablePlansForUser(UUID userId);
    UserSubscriptionResponseDTO redeemSubscription(UUID userId, UUID planId);
    UserSubscriptionResponseDTO giftSubscription(GiftSubscriptionRequestDTO request);
}
