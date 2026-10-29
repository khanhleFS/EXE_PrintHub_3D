package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.subscription.CreateSubscriptionPurchaseRequestDTO;
import com.fpt.printhub_3d.dto.subscription.GiftSubscriptionRequestDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanResponseDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPurchaseResponseDTO;
import com.fpt.printhub_3d.dto.subscription.UserSubscriptionResponseDTO;
import com.fpt.printhub_3d.dto.subscription.UserSubscriptionSummaryDTO;
import com.fpt.printhub_3d.entity.SubscriptionPurchase;
import com.fpt.printhub_3d.entity.UserSubscription;

import java.util.List;
import java.util.UUID;

public interface UserSubscriptionService {
    List<SubscriptionPlanResponseDTO> getAvailablePlansForUser(UUID userId);
    UserSubscriptionResponseDTO redeemSubscription(UUID userId, UUID planId);
    UserSubscriptionResponseDTO redeemSubscription(UUID userId, UUID planId, String idempotencyKey);
    UserSubscriptionResponseDTO giftSubscription(GiftSubscriptionRequestDTO request);
    UserSubscriptionSummaryDTO getUserSubscriptionSummary(UUID userId);

    SubscriptionPurchaseResponseDTO createPurchase(UUID userId, CreateSubscriptionPurchaseRequestDTO request);
    SubscriptionPurchaseResponseDTO getPurchaseById(UUID purchaseId, UUID userId);
    List<SubscriptionPurchaseResponseDTO> getMyPurchases(UUID userId);
    UserSubscription activateSubscriptionFromPurchase(SubscriptionPurchase purchase);
}
