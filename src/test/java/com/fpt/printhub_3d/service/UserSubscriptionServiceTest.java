package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.common.exception.ApiException;
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
        plan.setType(SubscriptionType.CUSTOMER);
        plan.setId(UUID.randomUUID());

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findByTypeAndIsActiveTrue(SubscriptionType.CUSTOMER))
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

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(pointWalletRepository.findById(userId)).thenReturn(Optional.of(wallet));
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(user))
                .thenReturn(List.of());
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

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(user))
                .thenReturn(List.of());
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

    @Test
    void testRedeemSubscription_ConsecutiveRenewal() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("renew_user")
                .role(UserRole.USER)
                .build();

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("VIP Gói Tháng")
                .type(SubscriptionType.CUSTOMER_VIP)
                .price(BigDecimal.valueOf(50000))
                .benefits("Freeship toàn sàn")
                .freeShipping(true)
                .shippingDiscountAmount(BigDecimal.valueOf(15000))
                .requiredPoints(100)
                .isActive(true)
                .build();
        plan.setId(planId);

        java.time.LocalDateTime existingEndDate = java.time.LocalDateTime.now().plusDays(15);
        UserSubscription existingSub = UserSubscription.builder()
                .user(user)
                .plan(plan)
                .startDate(java.time.LocalDateTime.now().minusDays(15))
                .endDate(existingEndDate)
                .isActive(true)
                .durationDays(30)
                .build();

        PointWallet wallet = PointWallet.builder()
                .userId(userId)
                .user(user)
                .balance(300)
                .build();

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(pointWalletRepository.findById(userId)).thenReturn(Optional.of(wallet));
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(user))
                .thenReturn(List.of(existingSub));
        when(userSubscriptionRepository.findAllActiveAndFutureSubscriptionsByUser(user))
                .thenReturn(List.of(existingSub));
        when(userSubscriptionRepository.findTopByUserIdAndPlanIdOrderByEndDateDesc(userId, planId))
                .thenReturn(Optional.of(existingSub));

        org.mockito.ArgumentCaptor<UserSubscription> captor = org.mockito.ArgumentCaptor.forClass(UserSubscription.class);
        when(userSubscriptionRepository.save(captor.capture())).thenAnswer(i -> {
            UserSubscription sub = i.getArgument(0);
            sub.setId(UUID.randomUUID());
            return sub;
        });

        UserSubscriptionResponseDTO response = userSubscriptionService.redeemSubscription(userId, planId);

        assertNotNull(response);
        UserSubscription savedSub = captor.getValue();
        // Kiểm tra nối tiếp kỳ cuối đã mua
        assertEquals(existingEndDate, savedSub.getStartDate(), "Kỳ gia hạn mới phải bắt đầu từ ngày kết thúc của kỳ trước");
        assertEquals(existingEndDate.plusDays(30), savedSub.getEndDate(), "Kỳ gia hạn mới phải kéo dài đúng 30 ngày từ ngày kết thúc kỳ trước");
        // Kiểm tra snapshot
        assertEquals(BigDecimal.valueOf(50000), savedSub.getAppliedPrice());
        assertEquals(100, savedSub.getAppliedRequiredPoints());
        assertTrue(savedSub.getAppliedFreeShipping());
        assertEquals("POINTS_REDEEM", savedSub.getActivationSource());
    }

    @Test
    void testRedeemSubscription_SwitchPlan_BlockedWhenOldPlanStillActive() {
        UUID userId = UUID.randomUUID();
        UUID oldPlanId = UUID.randomUUID();
        UUID newPlanId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("switch_user")
                .role(UserRole.USER)
                .build();

        SubscriptionPlan oldPlan = SubscriptionPlan.builder()
                .name("VIP Gói Cũ")
                .type(SubscriptionType.CUSTOMER)
                .price(BigDecimal.valueOf(30000))
                .requiredPoints(50)
                .isActive(true)
                .build();
        oldPlan.setId(oldPlanId);

        SubscriptionPlan newPlan = SubscriptionPlan.builder()
                .name("VIP Gói Mới")
                .type(SubscriptionType.CUSTOMER_VIP)
                .price(BigDecimal.valueOf(90000))
                .requiredPoints(150)
                .isActive(true)
                .build();
        newPlan.setId(newPlanId);

        UserSubscription oldActiveSub = UserSubscription.builder()
                .user(user)
                .plan(oldPlan)
                .startDate(java.time.LocalDateTime.now().minusDays(10))
                .endDate(java.time.LocalDateTime.now().plusDays(20))
                .isActive(true)
                .durationDays(30)
                .build();

        PointWallet wallet = PointWallet.builder()
                .userId(userId)
                .user(user)
                .balance(500)
                .build();

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(newPlanId)).thenReturn(Optional.of(newPlan));
        when(pointWalletRepository.findById(userId)).thenReturn(Optional.of(wallet));
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(user))
                .thenReturn(List.of(oldActiveSub));
        when(userSubscriptionRepository.findAllActiveAndFutureSubscriptionsByUser(user))
                .thenReturn(List.of(oldActiveSub));

        // P1.4 / SUB-03: Chặn thao tác đổi gói nếu còn kỳ cũ hiệu lực, không tự ý hủy kỳ cũ của khách
        ApiException ex = assertThrows(
                ApiException.class,
                () -> userSubscriptionService.redeemSubscription(userId, newPlanId)
        );

        assertTrue(ex.getMessage().contains("VIP Gói Cũ"), "Thông báo phải giải thích rõ gói cũ còn hiệu lực");
        assertTrue(oldActiveSub.getIsActive(), "Kỳ gói cũ không được phép bị âm thầm hủy");
        assertEquals(500, wallet.getBalance(), "Điểm thưởng không được phép bị trừ khi đổi gói thất bại");
    }

    @Test
    void testRedeemSubscription_SwitchPlan_AllowedWhenOldPlanExpired() {
        UUID userId = UUID.randomUUID();
        UUID oldPlanId = UUID.randomUUID();
        UUID newPlanId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .username("expired_switch_user")
                .role(UserRole.USER)
                .build();

        SubscriptionPlan oldPlan = SubscriptionPlan.builder()
                .name("VIP Gói Đã Hết Hạn")
                .type(SubscriptionType.CUSTOMER)
                .price(BigDecimal.valueOf(30000))
                .requiredPoints(50)
                .isActive(true)
                .build();
        oldPlan.setId(oldPlanId);

        SubscriptionPlan newPlan = SubscriptionPlan.builder()
                .name("VIP Gói Mới")
                .type(SubscriptionType.CUSTOMER_VIP)
                .price(BigDecimal.valueOf(90000))
                .freeShipping(true)
                .durationDays(30)
                .requiredPoints(150)
                .isActive(true)
                .build();
        newPlan.setId(newPlanId);

        UserSubscription oldExpiredSub = UserSubscription.builder()
                .user(user)
                .plan(oldPlan)
                .startDate(java.time.LocalDateTime.now().minusDays(40))
                .endDate(java.time.LocalDateTime.now().minusDays(10))
                .isActive(false)
                .durationDays(30)
                .build();

        PointWallet wallet = PointWallet.builder()
                .userId(userId)
                .user(user)
                .balance(500)
                .build();

        when(userRepository.findLockedById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(newPlanId)).thenReturn(Optional.of(newPlan));
        when(pointWalletRepository.findById(userId)).thenReturn(Optional.of(wallet));
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(user))
                .thenReturn(List.of());
        when(userSubscriptionRepository.findAllActiveAndFutureSubscriptionsByUser(user))
                .thenReturn(List.of(oldExpiredSub));
        when(userSubscriptionRepository.findTopByUserIdAndPlanIdOrderByEndDateDesc(userId, newPlanId))
                .thenReturn(Optional.empty());

        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(i -> {
            UserSubscription sub = i.getArgument(0);
            sub.setId(UUID.randomUUID());
            return sub;
        });

        UserSubscriptionResponseDTO response = userSubscriptionService.redeemSubscription(userId, newPlanId);

        assertNotNull(response);
        assertEquals("VIP Gói Mới", response.planName());
        assertEquals(350, wallet.getBalance());
        assertTrue(response.isActive());
    }
}
