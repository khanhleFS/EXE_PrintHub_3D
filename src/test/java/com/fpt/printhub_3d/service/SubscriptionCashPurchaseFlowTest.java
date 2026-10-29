package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.SubscriptionErrorCode;
import com.fpt.printhub_3d.dto.finance.RevenueAnalyticsResponseDTO;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkResponseDTO;
import com.fpt.printhub_3d.dto.subscription.CreateSubscriptionPurchaseRequestDTO;
import com.fpt.printhub_3d.dto.subscription.SubscriptionPurchaseResponseDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.SubscriptionType;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.impl.FinanceServiceImpl;
import com.fpt.printhub_3d.service.impl.PaymentServiceImpl;
import com.fpt.printhub_3d.service.impl.UserSubscriptionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SubscriptionCashPurchaseFlowTest {

    private UserSubscriptionServiceImpl userSubscriptionService;
    private PaymentServiceImpl paymentService;
    private FinanceServiceImpl financeService;

    @Mock private UserRepository userRepository;
    @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock private UserSubscriptionRepository userSubscriptionRepository;
    @Mock private SubscriptionPurchaseRepository subscriptionPurchaseRepository;
    @Mock private PointWalletRepository pointWalletRepository;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private SubscriptionCommandRecordRepository subscriptionCommandRecordRepository;

    @Mock private OrderRepository orderRepository;
    @Mock private CustomOrderRepository customOrderRepository;
    @Mock private CheckoutOrderRepository checkoutOrderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private PayOS payOS;
    @Mock private NotificationService notificationService;
    @Mock private InventoryService inventoryService;

    private User testUser;
    private SubscriptionPlan testPlan;
    private SubscriptionPlan testPlanB;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        userSubscriptionService = new UserSubscriptionServiceImpl(
                userRepository,
                subscriptionPlanRepository,
                userSubscriptionRepository,
                pointWalletRepository,
                pointTransactionRepository,
                subscriptionCommandRecordRepository,
                subscriptionPurchaseRepository
        );

        paymentService = new PaymentServiceImpl(
                orderRepository,
                customOrderRepository,
                checkoutOrderRepository,
                paymentRepository,
                subscriptionPurchaseRepository,
                userSubscriptionService,
                payOS,
                notificationService,
                inventoryService
        );

        financeService = new FinanceServiceImpl(
                orderRepository,
                paymentRepository,
                pointWalletRepository,
                subscriptionPurchaseRepository
        );

        testUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Nguyen Van A")
                .role(UserRole.USER)
                .isActive(true)
                .build();

        testPlan = SubscriptionPlan.builder()
                .type(SubscriptionType.CUSTOMER)
                .name("VIP Pro Plan")
                .price(new BigDecimal("99000"))
                .benefits("Freeship 5 orders per month")
                .durationDays(30)
                .isActive(true)
                .build();
        testPlan.setId(UUID.randomUUID());

        testPlanB = SubscriptionPlan.builder()
                .type(SubscriptionType.CUSTOMER_VIP)
                .name("Diamond VIP Plan")
                .price(new BigDecimal("199000"))
                .benefits("Unlimited priority support")
                .durationDays(60)
                .isActive(true)
                .build();
        testPlanB.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("FLOW_08.1: Tạo đơn mua gói thành công với giá và thời hạn snapshot chính xác")
    void testCreatePurchase_Success() {
        when(userRepository.findLockedById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(subscriptionPlanRepository.findById(testPlan.getId())).thenReturn(Optional.of(testPlan));
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(testUser)).thenReturn(List.of());
        when(subscriptionPurchaseRepository.save(any(SubscriptionPurchase.class)))
                .thenAnswer(inv -> {
                    SubscriptionPurchase p = inv.getArgument(0);
                    p.setId(UUID.randomUUID());
                    return p;
                });

        CreateSubscriptionPurchaseRequestDTO request = CreateSubscriptionPurchaseRequestDTO.builder()
                .planId(testPlan.getId())
                .idempotencyKey("KEY_PURCHASE_001")
                .build();

        SubscriptionPurchaseResponseDTO response = userSubscriptionService.createPurchase(testUser.getId(), request);

        assertNotNull(response);
        assertNotNull(response.purchaseId());
        assertEquals("VIP Pro Plan", response.planName());
        assertEquals(new BigDecimal("99000"), response.amount());
        assertEquals(30, response.durationDays());
        assertEquals("PENDING", response.status());
        assertEquals("KEY_PURCHASE_001", response.idempotencyKey());

        verify(subscriptionPurchaseRepository, times(1)).save(any(SubscriptionPurchase.class));
    }

    @Test
    @DisplayName("FLOW_08.2: Từ chối tạo đơn mua khi gói hội viên không hoạt động (isActive = false)")
    void testCreatePurchase_PlanInactive_ThrowsException() {
        testPlan.setIsActive(false);
        when(userRepository.findLockedById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(subscriptionPlanRepository.findById(testPlan.getId())).thenReturn(Optional.of(testPlan));

        CreateSubscriptionPurchaseRequestDTO request = CreateSubscriptionPurchaseRequestDTO.builder()
                .planId(testPlan.getId())
                .build();

        ApiException ex = assertThrows(ApiException.class, () ->
                userSubscriptionService.createPurchase(testUser.getId(), request));

        assertEquals(SubscriptionErrorCode.PLAN_INACTIVE.getCode(), ex.getErrorCode().getCode());
        verify(subscriptionPurchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("FLOW_08.3: Từ chối tạo đơn mua nếu gói chưa cấu hình giá hoặc giá <= 0")
    void testCreatePurchase_ZeroOrNullPrice_ThrowsException() {
        testPlan.setPrice(BigDecimal.ZERO);
        when(userRepository.findLockedById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(subscriptionPlanRepository.findById(testPlan.getId())).thenReturn(Optional.of(testPlan));

        CreateSubscriptionPurchaseRequestDTO request = CreateSubscriptionPurchaseRequestDTO.builder()
                .planId(testPlan.getId())
                .build();

        ApiException ex = assertThrows(ApiException.class, () ->
                userSubscriptionService.createPurchase(testUser.getId(), request));

        assertEquals(SubscriptionErrorCode.MONEY_PURCHASE_NOT_SUPPORTED.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("FLOW_08.4: Từ chối tạo đơn mua khi khách hàng đang có gói active khác (ACTIVE_PLAN_CONFLICT)")
    void testCreatePurchase_ActivePlanConflict_ThrowsException() {
        when(userRepository.findLockedById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(subscriptionPlanRepository.findById(testPlanB.getId())).thenReturn(Optional.of(testPlanB));

        // User is currently active with testPlan A
        UserSubscription currentActiveSub = UserSubscription.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .plan(testPlan)
                .startDate(LocalDateTime.now().minusDays(5))
                .endDate(LocalDateTime.now().plusDays(25))
                .isActive(true)
                .build();
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(testUser))
                .thenReturn(List.of(currentActiveSub));

        CreateSubscriptionPurchaseRequestDTO request = CreateSubscriptionPurchaseRequestDTO.builder()
                .planId(testPlanB.getId()) // Different plan!
                .build();

        ApiException ex = assertThrows(ApiException.class, () ->
                userSubscriptionService.createPurchase(testUser.getId(), request));

        assertEquals(SubscriptionErrorCode.ACTIVE_PLAN_CONFLICT.getCode(), ex.getErrorCode().getCode());
        verify(subscriptionPurchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("FLOW_08.5: Cho phép gia hạn khi user đang có gói active cùng planId")
    void testCreatePurchase_SamePlanRenewal_Success() {
        when(userRepository.findLockedById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(subscriptionPlanRepository.findById(testPlan.getId())).thenReturn(Optional.of(testPlan));

        // User already has testPlan active
        UserSubscription currentActiveSub = UserSubscription.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .plan(testPlan)
                .startDate(LocalDateTime.now().minusDays(10))
                .endDate(LocalDateTime.now().plusDays(20))
                .isActive(true)
                .build();
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(testUser))
                .thenReturn(List.of(currentActiveSub));

        when(subscriptionPurchaseRepository.save(any(SubscriptionPurchase.class)))
                .thenAnswer(inv -> {
                    SubscriptionPurchase p = inv.getArgument(0);
                    p.setId(UUID.randomUUID());
                    return p;
                });

        CreateSubscriptionPurchaseRequestDTO request = CreateSubscriptionPurchaseRequestDTO.builder()
                .planId(testPlan.getId()) // Same plan -> renewal
                .build();

        SubscriptionPurchaseResponseDTO response = userSubscriptionService.createPurchase(testUser.getId(), request);

        assertNotNull(response);
        assertEquals("PENDING", response.status());
        verify(subscriptionPurchaseRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("FLOW_08.6: Idempotency Replay trả lại bản ghi cũ khi trùng idempotencyKey")
    void testCreatePurchase_IdempotencyReplay() {
        SubscriptionPurchase existingPurchase = SubscriptionPurchase.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .plan(testPlan)
                .amount(testPlan.getPrice())
                .durationDays(30)
                .status("PENDING")
                .idempotencyKey("KEY_REPLAY_123")
                .createdAt(Instant.now().minusSeconds(60))
                .build();

        when(subscriptionPurchaseRepository.findByIdempotencyKey("KEY_REPLAY_123"))
                .thenReturn(Optional.of(existingPurchase));

        CreateSubscriptionPurchaseRequestDTO request = CreateSubscriptionPurchaseRequestDTO.builder()
                .planId(testPlan.getId())
                .idempotencyKey("KEY_REPLAY_123")
                .build();

        SubscriptionPurchaseResponseDTO response = userSubscriptionService.createPurchase(testUser.getId(), request);

        assertNotNull(response);
        assertEquals(existingPurchase.getId(), response.purchaseId());
        // Không tạo bản ghi mới vào DB
        verify(subscriptionPurchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("FLOW_08.7: Tạo link PayOS thành công cho giao dịch mua gói hội viên")
    void testCreatePaymentLink_Subscription_Success() {
        SubscriptionPurchase purchase = SubscriptionPurchase.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .plan(testPlan)
                .amount(new BigDecimal("99000"))
                .durationDays(30)
                .status("PENDING")
                .createdAt(Instant.now())
                .build();

        when(subscriptionPurchaseRepository.findLockedById(purchase.getId()))
                .thenReturn(Optional.of(purchase));
        when(paymentRepository.findBySubscriptionPurchaseId(purchase.getId()))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByTransactionId(anyString()))
                .thenReturn(Optional.empty());

        CreatePaymentLinkResponse payOsResponse = mock(CreatePaymentLinkResponse.class);
        when(payOsResponse.getCheckoutUrl()).thenReturn("https://payos.vn/checkout/sub_123456");
        when(payOS.paymentRequests().create(any(CreatePaymentLinkRequest.class))).thenReturn(payOsResponse);

        CreatePaymentLinkRequestDTO request = CreatePaymentLinkRequestDTO.builder()
                .orderId(purchase.getId())
                .orderType("SUBSCRIPTION_PURCHASE")
                .build();

        CreatePaymentLinkResponseDTO response = paymentService.createPaymentLink(testUser.getId(), request);

        assertNotNull(response);
        assertEquals("https://payos.vn/checkout/sub_123456", response.paymentLinkUrl());
        assertEquals(new BigDecimal("99000"), response.amount());

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(1)).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();

        assertEquals("PAYOS", savedPayment.getGateway());
        assertEquals("PENDING", savedPayment.getStatus());
        assertEquals(purchase, savedPayment.getSubscriptionPurchase());
    }

    @Test
    @DisplayName("FLOW_08.8: Từ chối tạo link PayOS nếu không phải chủ sở hữu giao dịch mua gói (403 Forbidden)")
    void testCreatePaymentLink_Subscription_ForbiddenForNonOwner() {
        SubscriptionPurchase purchase = SubscriptionPurchase.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .plan(testPlan)
                .amount(new BigDecimal("99000"))
                .status("PENDING")
                .build();

        when(subscriptionPurchaseRepository.findLockedById(purchase.getId()))
                .thenReturn(Optional.of(purchase));

        UUID anotherUserId = UUID.randomUUID();
        CreatePaymentLinkRequestDTO request = CreatePaymentLinkRequestDTO.builder()
                .orderId(purchase.getId())
                .orderType("SUBSCRIPTION_PURCHASE")
                .build();

        assertThrows(ResponseStatusException.class, () ->
                paymentService.createPaymentLink(anotherUserId, request));
    }

    @Test
    @DisplayName("FLOW_08.9: Settle thanh toán thành công, kích hoạt gói hội viên và gia hạn chính xác")
    void testSettle_SubscriptionPayment_SuccessAndActivatesPlan() throws Exception {
        SubscriptionPurchase purchase = SubscriptionPurchase.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .plan(testPlan)
                .amount(new BigDecimal("99000"))
                .durationDays(30)
                .status("PENDING")
                .createdAt(Instant.now().minusSeconds(120))
                .build();

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setSubscriptionPurchase(purchase);
        payment.setStatus("PENDING");
        payment.setAmount(new BigDecimal("99000"));
        payment.setTransactionId("PAYOS_TXN_001");

        when(userSubscriptionRepository.findActiveSubscriptionsByUser(testUser)).thenReturn(List.of());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

        // Invoke private settle via reflection
        Method settleMethod = PaymentServiceImpl.class.getDeclaredMethod("settle", Payment.class, BigDecimal.class);
        settleMethod.setAccessible(true);
        settleMethod.invoke(paymentService, payment, new BigDecimal("99000"));

        assertEquals("SUCCESS", payment.getStatus());
        assertEquals("PAID", purchase.getStatus());
        assertNotNull(purchase.getSettledAt());

        // Kích hoạt gói mới
        verify(userSubscriptionRepository, times(1)).save(any(UserSubscription.class));
        verify(subscriptionPurchaseRepository, times(1)).save(purchase);
        verify(notificationService, times(1)).sendNotification(
                eq(testUser), anyString(), contains("VIP Pro Plan"), eq("PAYMENT"), eq("/profile")
        );
    }

    @Test
    @DisplayName("FLOW_08.10: BẤT BIẾN CỐT LÕI - Settle mua gói hội viên TUYỆT ĐỐI KHÔNG cộng điểm thưởng (Zero Rewards)")
    void testSettle_ZeroRewardPoints_Invariant() throws Exception {
        SubscriptionPurchase purchase = SubscriptionPurchase.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .plan(testPlan)
                .amount(new BigDecimal("99000"))
                .durationDays(30)
                .status("PENDING")
                .createdAt(Instant.now())
                .build();

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setSubscriptionPurchase(purchase);
        payment.setStatus("PENDING");
        payment.setAmount(new BigDecimal("99000"));
        payment.setTransactionId("PAYOS_TXN_002");

        when(userSubscriptionRepository.findActiveSubscriptionsByUser(testUser)).thenReturn(List.of());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

        Method settleMethod = PaymentServiceImpl.class.getDeclaredMethod("settle", Payment.class, BigDecimal.class);
        settleMethod.setAccessible(true);
        settleMethod.invoke(paymentService, payment, new BigDecimal("99000"));

        // TUYỆT ĐỐI KHÔNG GỌI VÍ ĐIỂM HOẶC LƯU GIAO DỊCH ĐIỂM
        verify(pointWalletRepository, never()).findByUserIdForUpdate(any());
        verify(pointWalletRepository, never()).save(any());
        verify(pointTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("FLOW_08.11: Finance Service ghi nhận đúng doanh thu từ các gói đã thanh toán (settled)")
    void testFinanceRevenueAnalytics_IncludesSubscriptionRevenue() {
        Instant from = Instant.now().minusSeconds(86400 * 7);
        Instant to = Instant.now();

        when(orderRepository.sumCommissionFeeBetween(from, to)).thenReturn(new BigDecimal("50000"));
        when(subscriptionPurchaseRepository.sumSettledRevenueBetween(from, to)).thenReturn(new BigDecimal("198000"));

        RevenueAnalyticsResponseDTO analytics = financeService.getRevenueAnalytics(from, to);

        assertNotNull(analytics);
        assertEquals(new BigDecimal("50000"), analytics.orderCommissionRevenue());
        assertEquals(new BigDecimal("198000"), analytics.customerSubscriptionRevenue());
        assertEquals(new BigDecimal("248000"), analytics.totalRevenue(), "Tổng doanh thu phải gồm hoa hồng sàn + doanh thu gói hội viên");
    }
}
