package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.subscription.GiftSubscriptionRequestDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanResponseDTO;
import com.fpt.printhub_3d.dto.subscription.UserSubscriptionResponseDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.SubscriptionType;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.impl.UserSubscriptionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserSubscriptionServiceTest {

    private UserSubscriptionService userSubscriptionService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private PointWalletRepository pointWalletRepository;
    @Mock
    private PointTransactionRepository pointTransactionRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userSubscriptionService = new UserSubscriptionServiceImpl(
                userRepository,
                subscriptionPlanRepository,
                userSubscriptionRepository,
                pointWalletRepository,
                pointTransactionRepository
        );
    }

    @Test
    void testGetAvailablePlansForUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .role(UserRole.USER)
                .build();

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("VIP Gói")
                .type(SubscriptionType.CUSTOMER_VIP)
                .price(BigDecimal.TEN)
                .benefits("Free ship")
                .requiredPoints(100)
                .isActive(true)
                .build();
        plan.setId(UUID.randomUUID());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findByTypeAndIsActiveTrue(SubscriptionType.CUSTOMER_VIP))
                .thenReturn(List.of(plan));

        List<SubscriptionPlanResponseDTO> plans = userSubscriptionService.getAvailablePlansForUser(userId);

        assertNotNull(plans);
        assertEquals(1, plans.size());
        assertEquals("VIP Gói", plans.get(0).name());
    }

    @Test
    void testRedeemSubscription_Success() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("test_user")
                .role(UserRole.USER)
                .build();

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("VIP Plan")
                .type(SubscriptionType.CUSTOMER_VIP)
                .price(BigDecimal.TEN)
                .benefits("Benefits")
                .requiredPoints(100)
                .isActive(true)
                .build();
        plan.setId(planId);

        PointWallet wallet = PointWallet.builder()
                .userId(userId)
                .user(user)
                .balance(500)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(pointWalletRepository.findById(userId)).thenReturn(Optional.of(wallet));
        when(userSubscriptionRepository.findActiveSubscriptionByUserAndType(user, SubscriptionType.CUSTOMER_VIP))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(i -> {
            UserSubscription sub = i.getArgument(0);
            sub.setId(UUID.randomUUID());
            return sub;
        });

        UserSubscriptionResponseDTO response = userSubscriptionService.redeemSubscription(userId, planId);

        assertNotNull(response);
        assertEquals("VIP Plan", response.planName());
        assertEquals(400, wallet.getBalance());
        verify(pointWalletRepository, times(1)).save(wallet);
        verify(pointTransactionRepository, times(1)).save(any(PointTransaction.class));
        verify(userSubscriptionRepository, times(1)).save(any(UserSubscription.class));
    }

    @Test
    void testGiftSubscription_Success() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("gift_user")
                .role(UserRole.USER)
                .build();

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("VIP Gift Plan")
                .type(SubscriptionType.CUSTOMER_VIP)
                .price(BigDecimal.TEN)
                .benefits("Benefits")
                .requiredPoints(100)
                .isActive(true)
                .build();
        plan.setId(planId);

        GiftSubscriptionRequestDTO request = new GiftSubscriptionRequestDTO(userId, planId, "Event");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(userSubscriptionRepository.findActiveSubscriptionByUserAndType(user, SubscriptionType.CUSTOMER_VIP))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(i -> {
            UserSubscription sub = i.getArgument(0);
            sub.setId(UUID.randomUUID());
            return sub;
        });

        UserSubscriptionResponseDTO response = userSubscriptionService.giftSubscription(request);

        assertNotNull(response);
        assertEquals("VIP Gift Plan", response.planName());
        assertTrue(response.isActive());
        verify(userSubscriptionRepository, times(1)).save(any(UserSubscription.class));
    }
}
