package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.SubscriptionErrorCode;
import com.fpt.printhub_3d.dto.subscription.GiftSubscriptionRequestDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPlanResponseDTO;
import com.fpt.printhub_3d.dto.subscription.UserSubscriptionResponseDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import com.fpt.printhub_3d.entity.Enumeration.SubscriptionType;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.impl.UserSubscriptionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    @Mock
    private SubscriptionCommandRecordRepository subscriptionCommandRecordRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userSubscriptionService = new UserSubscriptionServiceImpl(
                userRepository,
                subscriptionPlanRepository,
                userSubscriptionRepository,
                pointWalletRepository,
                pointTransactionRepository,
                subscriptionCommandRecordRepository
        );
    }

    @Test
    void testGetAvailablePlansForUser_ReturnsCustomerAndVip() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .role(UserRole.USER)
                .build();

        SubscriptionPlan plan1 = SubscriptionPlan.builder()
                .name("Standard Plan")
                .type(SubscriptionType.CUSTOMER)
                .price(BigDecimal.valueOf(50000))
                .benefits("Free ship")
                .requiredPoints(100)
                .durationDays(30)
                .isActive(true)
                .build();
        plan1.setId(UUID.randomUUID());

        SubscriptionPlan plan2 = SubscriptionPlan.builder()
                .name("VIP Plan")
                .type(SubscriptionType.CUSTOMER_VIP)
                .price(BigDecimal.valueOf(100000))
                .benefits("VIP support")
                .requiredPoints(200)
                .durationDays(60)
                .isActive(true)
                .build();
        plan2.setId(UUID.randomUUID());

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findByTypeInAndIsActiveTrue(List.of(SubscriptionType.CUSTOMER, SubscriptionType.CUSTOMER_VIP)))
                .thenReturn(List.of(plan1, plan2));

        List<SubscriptionPlanResponseDTO> plans = userSubscriptionService.getAvailablePlansForUser(userId);

        assertNotNull(plans);
        assertEquals(2, plans.size());
        assertEquals(30, plans.get(0).durationDays());
        assertEquals(60, plans.get(1).durationDays());
    }

    @Test
    void testRedeemSubscription_Success_NewSubscription() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("test_user")
                .role(UserRole.USER)
                .isActive(true)
                .build();

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("VIP Plan")
                .type(SubscriptionType.CUSTOMER_VIP)
                .price(BigDecimal.TEN)
                .benefits("Benefits")
                .requiredPoints(100)
                .durationDays(45)
                .isActive(true)
                .build();
        plan.setId(planId);

        PointWallet wallet = PointWallet.builder()
                .userId(userId)
                .user(user)
                .balance(500)
                .build();

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(user)).thenReturn(List.of());
        when(pointWalletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(i -> {
            UserSubscription sub = i.getArgument(0);
            sub.setId(UUID.randomUUID());
            return sub;
        });

        UserSubscriptionResponseDTO response = userSubscriptionService.redeemSubscription(userId, planId, "REQ_REDEEM_1");

        assertNotNull(response);
        assertEquals("VIP Plan", response.planName());
        assertEquals(400, wallet.getBalance());
        verify(pointWalletRepository, times(1)).save(wallet);
        verify(pointTransactionRepository, times(1)).save(any(PointTransaction.class));
        verify(userSubscriptionRepository, times(1)).save(any(UserSubscription.class));
        verify(subscriptionCommandRecordRepository, times(1)).save(any(SubscriptionCommandRecord.class));
    }

    @Test
    void testRedeemSubscription_Success_ExtendExistingPlan() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("test_user")
                .role(UserRole.USER)
                .isActive(true)
                .build();

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("Gold Plan")
                .type(SubscriptionType.CUSTOMER)
                .price(BigDecimal.TEN)
                .benefits("Benefits")
                .requiredPoints(100)
                .durationDays(30)
                .isActive(true)
                .build();
        plan.setId(planId);

        LocalDateTime currentEnd = LocalDateTime.now().plusDays(10);
        UserSubscription existingSub = UserSubscription.builder()
                .user(user)
                .plan(plan)
                .startDate(LocalDateTime.now().minusDays(20))
                .endDate(currentEnd)
                .isActive(true)
                .build();
        existingSub.setId(UUID.randomUUID());

        PointWallet wallet = PointWallet.builder()
                .userId(userId)
                .user(user)
                .balance(300)
                .build();

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(user)).thenReturn(List.of(existingSub));
        when(pointWalletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(i -> i.getArgument(0));

        UserSubscriptionResponseDTO response = userSubscriptionService.redeemSubscription(userId, planId);

        assertNotNull(response);
        assertEquals("Gold Plan", response.planName());
        assertEquals(200, wallet.getBalance());
        assertTrue(response.endDate().isAfter(currentEnd));
        verify(pointTransactionRepository, times(1)).save(any(PointTransaction.class));
    }

    @Test
    void testRedeemSubscription_PlanConflict_DoesNotDebitPoints() {
        UUID userId = UUID.randomUUID();
        UUID currentPlanId = UUID.randomUUID();
        UUID newPlanId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("test_user")
                .role(UserRole.USER)
                .isActive(true)
                .build();

        SubscriptionPlan currentPlan = SubscriptionPlan.builder()
                .name("Silver Plan")
                .type(SubscriptionType.CUSTOMER)
                .isActive(true)
                .build();
        currentPlan.setId(currentPlanId);

        SubscriptionPlan newPlan = SubscriptionPlan.builder()
                .name("Platinum Plan")
                .type(SubscriptionType.CUSTOMER_VIP)
                .requiredPoints(500)
                .durationDays(30)
                .isActive(true)
                .build();
        newPlan.setId(newPlanId);

        UserSubscription existingSub = UserSubscription.builder()
                .user(user)
                .plan(currentPlan)
                .startDate(LocalDateTime.now().minusDays(5))
                .endDate(LocalDateTime.now().plusDays(25))
                .isActive(true)
                .build();

        PointWallet wallet = PointWallet.builder()
                .userId(userId)
                .user(user)
                .balance(1000)
                .build();

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(newPlanId)).thenReturn(Optional.of(newPlan));
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(user)).thenReturn(List.of(existingSub));
        when(pointWalletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));

        ApiException exception = assertThrows(ApiException.class, () ->
                userSubscriptionService.redeemSubscription(userId, newPlanId)
        );

        assertEquals(SubscriptionErrorCode.ACTIVE_PLAN_CONFLICT.getCode(), exception.getErrorCode().getCode());
        assertEquals(1000, wallet.getBalance()); // ĐIỂM HOÀN TOÀN KHÔNG BỊ TRỪ!
        verify(pointWalletRepository, never()).save(any());
        verify(pointTransactionRepository, never()).save(any());
        verify(userSubscriptionRepository, never()).save(any());
    }

    @Test
    void testRedeemSubscription_InsufficientPoints_ThrowsException() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .role(UserRole.USER)
                .isActive(true)
                .build();

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("VIP Plan")
                .type(SubscriptionType.CUSTOMER)
                .requiredPoints(500)
                .isActive(true)
                .build();
        plan.setId(planId);

        PointWallet wallet = PointWallet.builder()
                .userId(userId)
                .balance(100) // Thiếu điểm
                .build();

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(user)).thenReturn(List.of());
        when(pointWalletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));

        ApiException exception = assertThrows(ApiException.class, () ->
                userSubscriptionService.redeemSubscription(userId, planId)
        );

        assertEquals(SubscriptionErrorCode.INSUFFICIENT_POINTS.getCode(), exception.getErrorCode().getCode());
        assertEquals(100, wallet.getBalance());
        verify(pointTransactionRepository, never()).save(any());
    }

    @Test
    void testRedeemSubscription_IdempotentReplay() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        String idempotencyKey = "KEY_REDEEM_DUPLICATE";

        User user = User.builder().id(userId).username("user1").build();
        SubscriptionPlan plan = SubscriptionPlan.builder().name("VIP Plan").type(SubscriptionType.CUSTOMER).build();
        plan.setId(planId);

        UserSubscription existingSub = UserSubscription.builder()
                .user(user)
                .plan(plan)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();
        existingSub.setId(subId);

        SubscriptionCommandRecord record = SubscriptionCommandRecord.builder()
                .requestId(idempotencyKey)
                .userId(userId)
                .planId(planId)
                .subscriptionId(subId)
                .commandType("REDEEM")
                .createdAt(LocalDateTime.now())
                .build();

        when(subscriptionCommandRecordRepository.findByRequestId(idempotencyKey)).thenReturn(Optional.of(record));
        when(userSubscriptionRepository.findById(subId)).thenReturn(Optional.of(existingSub));

        UserSubscriptionResponseDTO response = userSubscriptionService.redeemSubscription(userId, planId, idempotencyKey);

        assertNotNull(response);
        assertEquals(subId, response.subscriptionId());
        verify(pointWalletRepository, never()).findByUserIdForUpdate(any());
        verify(pointTransactionRepository, never()).save(any());
    }

    @Test
    void testGiftSubscription_Success_NewSubscription() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("gift_user")
                .role(UserRole.USER)
                .isActive(true)
                .build();

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("VIP Gift Plan")
                .type(SubscriptionType.CUSTOMER_VIP)
                .price(BigDecimal.TEN)
                .benefits("Benefits")
                .durationDays(60)
                .isActive(true)
                .build();
        plan.setId(planId);

        GiftSubscriptionRequestDTO request = new GiftSubscriptionRequestDTO("REQ_GIFT_001", userId, planId, "Event");

        when(subscriptionCommandRecordRepository.findByRequestId("REQ_GIFT_001")).thenReturn(Optional.empty());
        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(user)).thenReturn(List.of());
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
        verify(subscriptionCommandRecordRepository, times(1)).save(any(SubscriptionCommandRecord.class));
    }

    @Test
    void testGiftSubscription_Success_ExtendExistingPlan() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("gift_user")
                .role(UserRole.USER)
                .isActive(true)
                .build();

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("VIP Gift Plan")
                .type(SubscriptionType.CUSTOMER_VIP)
                .durationDays(30)
                .isActive(true)
                .build();
        plan.setId(planId);

        LocalDateTime currentEnd = LocalDateTime.now().plusDays(15);
        UserSubscription existingSub = UserSubscription.builder()
                .user(user)
                .plan(plan)
                .startDate(LocalDateTime.now().minusDays(15))
                .endDate(currentEnd)
                .isActive(true)
                .build();
        existingSub.setId(UUID.randomUUID());

        GiftSubscriptionRequestDTO request = new GiftSubscriptionRequestDTO(userId, planId, "Loyalty bonus");

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(user)).thenReturn(List.of(existingSub));
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(i -> i.getArgument(0));

        UserSubscriptionResponseDTO response = userSubscriptionService.giftSubscription(request);

        assertNotNull(response);
        assertTrue(response.endDate().isAfter(currentEnd));
        verify(userSubscriptionRepository, times(1)).save(existingSub);
    }

    @Test
    void testGiftSubscription_PlanConflict_ThrowsException() {
        UUID userId = UUID.randomUUID();
        UUID planAId = UUID.randomUUID();
        UUID planBId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .role(UserRole.USER)
                .isActive(true)
                .build();

        SubscriptionPlan planA = SubscriptionPlan.builder().name("Plan A").type(SubscriptionType.CUSTOMER).build();
        planA.setId(planAId);

        SubscriptionPlan planB = SubscriptionPlan.builder().name("Plan B").type(SubscriptionType.CUSTOMER_VIP).isActive(true).build();
        planB.setId(planBId);

        UserSubscription subA = UserSubscription.builder()
                .user(user)
                .plan(planA)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(20))
                .isActive(true)
                .build();

        GiftSubscriptionRequestDTO request = new GiftSubscriptionRequestDTO(userId, planBId, "Bonus");

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(planBId)).thenReturn(Optional.of(planB));
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(user)).thenReturn(List.of(subA));

        ApiException exception = assertThrows(ApiException.class, () ->
                userSubscriptionService.giftSubscription(request)
        );

        assertEquals(SubscriptionErrorCode.ACTIVE_PLAN_CONFLICT.getCode(), exception.getErrorCode().getCode());
        verify(userSubscriptionRepository, never()).save(any());
    }

    @Test
    void testGiftSubscription_IdempotentReplay() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        String requestId = "REQ_GIFT_REPLAY";

        User user = User.builder().id(userId).username("user1").build();
        SubscriptionPlan plan = SubscriptionPlan.builder().name("Plan A").type(SubscriptionType.CUSTOMER).build();
        plan.setId(planId);

        UserSubscription sub = UserSubscription.builder()
                .user(user)
                .plan(plan)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();
        sub.setId(subId);

        SubscriptionCommandRecord record = SubscriptionCommandRecord.builder()
                .requestId(requestId)
                .userId(userId)
                .planId(planId)
                .subscriptionId(subId)
                .commandType("GIFT")
                .createdAt(LocalDateTime.now())
                .build();

        GiftSubscriptionRequestDTO request = new GiftSubscriptionRequestDTO(requestId, userId, planId, "Event");

        when(subscriptionCommandRecordRepository.findByRequestId(requestId)).thenReturn(Optional.of(record));
        when(userSubscriptionRepository.findById(subId)).thenReturn(Optional.of(sub));

        UserSubscriptionResponseDTO response = userSubscriptionService.giftSubscription(request);

        assertNotNull(response);
        assertEquals(subId, response.subscriptionId());
        verify(userRepository, never()).findLockedById(any());
        verify(userSubscriptionRepository, never()).save(any());
    }

    @Test
    void testGiftSubscription_IdempotencyConflict() {
        UUID userId = UUID.randomUUID();
        UUID planAId = UUID.randomUUID();
        UUID planBId = UUID.randomUUID();
        String requestId = "REQ_SAME_KEY_DIFFERENT_PAYLOAD";

        SubscriptionCommandRecord record = SubscriptionCommandRecord.builder()
                .requestId(requestId)
                .userId(userId)
                .planId(planAId)
                .subscriptionId(UUID.randomUUID())
                .commandType("GIFT")
                .createdAt(LocalDateTime.now())
                .build();

        GiftSubscriptionRequestDTO request = new GiftSubscriptionRequestDTO(requestId, userId, planBId, "Different plan");

        when(subscriptionCommandRecordRepository.findByRequestId(requestId)).thenReturn(Optional.of(record));

        ApiException exception = assertThrows(ApiException.class, () ->
                userSubscriptionService.giftSubscription(request)
        );

        assertEquals(SubscriptionErrorCode.IDEMPOTENCY_CONFLICT.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    void testGiftSubscription_InactiveUser_ThrowsException() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .role(UserRole.USER)
                .isActive(false) // Bị khóa
                .build();

        GiftSubscriptionRequestDTO request = new GiftSubscriptionRequestDTO(userId, planId, "Gift");

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));

        ApiException exception = assertThrows(ApiException.class, () ->
                userSubscriptionService.giftSubscription(request)
        );

        assertEquals(SubscriptionErrorCode.INVALID_RECIPIENT.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    void testGiftSubscription_InactivePlan_ThrowsException() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .role(UserRole.USER)
                .isActive(true)
                .build();

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("Inactive Plan")
                .type(SubscriptionType.CUSTOMER)
                .isActive(false) // Gói không hoạt động
                .build();
        plan.setId(planId);

        GiftSubscriptionRequestDTO request = new GiftSubscriptionRequestDTO(userId, planId, "Gift");

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));

        ApiException exception = assertThrows(ApiException.class, () ->
                userSubscriptionService.giftSubscription(request)
        );

        assertEquals(SubscriptionErrorCode.PLAN_INACTIVE.getCode(), exception.getErrorCode().getCode());
    }
}
