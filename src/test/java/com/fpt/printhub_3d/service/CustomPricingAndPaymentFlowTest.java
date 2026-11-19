package com.fpt.printhub_3d.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.GlobalExceptionHandler;
import com.fpt.printhub_3d.dto.cart.CartItemRequestDTO;
import com.fpt.printhub_3d.dto.cart.CartUpdateRequestDTO;
import com.fpt.printhub_3d.dto.checkout.AdminCheckoutOrderStatusRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderBatchQuoteRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderBatchQuoteRequestDTO.ItemQuote;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderConfirmRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderQuoteRequestDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderStatusRequestDTO;
import com.fpt.printhub_3d.dto.order.PricingBreakdownDTO;
import com.fpt.printhub_3d.dto.order.PricingBreakdownDTO.PricingStage;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.dto.review.CustomOrderReviewRequestDTO;
import com.fpt.printhub_3d.dto.subscription.BenefitPreviewResponseDTO.BenefitStatus;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.OrderRewardStatus;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.impl.CartServiceImpl;
import com.fpt.printhub_3d.service.impl.CheckoutOrderServiceImpl;
import com.fpt.printhub_3d.service.impl.CustomOrderReviewServiceImpl;
import com.fpt.printhub_3d.service.impl.CustomOrderServiceImpl;
import com.fpt.printhub_3d.service.impl.DashboardServiceImpl;
import com.fpt.printhub_3d.service.impl.PaymentEvidenceServiceImpl;
import com.fpt.printhub_3d.service.impl.OrderRewardServiceImpl;
import com.fpt.printhub_3d.service.impl.PaymentServiceImpl;
import com.fpt.printhub_3d.service.impl.PointWalletLockHelper;
import com.fpt.printhub_3d.service.impl.SubscriptionBenefitServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.v2.paymentRequests.Transaction;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;
import vn.payos.service.blocking.webhooks.WebhooksService;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomPricingAndPaymentFlowTest {

    private CartService cartService;
    private CheckoutOrderService checkoutOrderService;
    private PaymentService paymentService;

    // Repositories & Mocks for CartService
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductService productService;

    // Repositories & Mocks for CheckoutOrderService
    @Mock private CheckoutOrderRepository checkoutOrderRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private CustomOrderRepository customOrderRepository;
    @Mock private PaymentService mockPaymentService;

    // Repositories & Mocks for PaymentService
    @Mock private PaymentRepository paymentRepository;
    @Mock private SubscriptionPurchaseRepository subscriptionPurchaseRepository;
    @Mock private UserSubscriptionService userSubscriptionService;
    @Mock private PayOS payOS;
    @Mock private NotificationService notificationService;
    @Mock private InventoryService mockInventoryService;
    @Mock private UserSubscriptionRepository userSubscriptionRepository;
    @Mock private SubscriptionUsageLogRepository usageLogRepository;
    @Mock private StoreSettingRepository storeSettingRepository;
    @Mock private UserRepository userRepository;

    private User testUser;
    private Cart testCart;
    private SubscriptionBenefitService benefitService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        cartService = new CartServiceImpl(cartRepository, cartItemRepository, productRepository, productService);
        benefitService = new SubscriptionBenefitServiceImpl(
                userSubscriptionRepository, usageLogRepository, new ObjectMapper());

        checkoutOrderService = new CheckoutOrderServiceImpl(
                checkoutOrderRepository,
                cartRepository,
                cartItemRepository,
                productRepository,
                orderRepository,
                orderItemRepository,
                customOrderRepository,
                mockPaymentService,
                mockInventoryService,
                userRepository, paymentRepository, new OrderTransitionPolicy(), notificationService,
                new ObjectMapper(), null, storeSettingRepository, benefitService
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
                mockInventoryService,
                benefitService
        );

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("student@fpt.edu.vn");

        testCart = new Cart();
        testCart.setId(UUID.randomUUID());
        testCart.setUser(testUser);
    }

    @Test
    void webhookAndVerifySettleEachCheckoutOnceEvenForEqualAmounts() {
        Payment first = stubCheckoutPayment("701", "70000");
        Payment second = stubCheckoutPayment("702", "70000");
        PaymentRequestsService gateway = stubPayosLinks();
        PaymentLink firstLink = paidPayosLink(701L, 70000L);
        PaymentLink secondLink = paidPayosLink(702L, 70000L);
        when(gateway.get(701L)).thenReturn(firstLink);
        when(gateway.get(702L)).thenReturn(secondLink);
        Webhook webhook = stubWebhook(701L, 70000L);

        paymentService.handleWebhook(webhook);
        paymentService.verify("701", testUser.getId());
        paymentService.handleWebhook(webhook);
        paymentService.verify("702", testUser.getId());

        assertEquals("SUCCESS", first.getStatus());
        assertEquals("SUCCESS", second.getStatus());
        assertEquals(Instant.parse("2026-11-19T03:15:00Z"), first.getPaidAt());
        assertEquals("PAID", first.getCheckoutOrder().getStatus());
        assertEquals("PAID", second.getCheckoutOrder().getStatus());
        verify(paymentRepository).save(first);
        verify(paymentRepository).save(second);
        verify(notificationService, times(2)).sendNotification(eq(testUser), any(), any(), eq("PAYMENT"), any());
    }

    @Test
    void mismatchedPaymentKeepsExpectedAmountAndPersistsObservedAmountForReconciliation() {
        Payment payment = stubCheckoutPayment("801", "90000");
        PaymentRequestsService gateway = stubPayosLinks();
        PaymentLink live = paidPayosLink(801L, 89000L);
        when(gateway.get(801L)).thenReturn(live);
        Webhook webhook = stubWebhook(801L, 89000L);

        paymentService.handleWebhook(webhook);
        var verified = paymentService.verify("801", testUser.getId());
        paymentService.handleWebhook(webhook);

        assertEquals(Payment.PENDING_RECONCILIATION, verified.get("status"));
        assertEquals(new BigDecimal("90000"), payment.getAmount());
        assertEquals(new BigDecimal("89000"), payment.getReconciliationAmount());
        assertEquals("AMOUNT_MISMATCH", payment.getReconciliationReason());
        assertEquals("CONFIRMED", payment.getCheckoutOrder().getStatus());
        assertNull(payment.getPaidAt());
        verify(paymentRepository).save(payment);
        verifyNoInteractions(notificationService, usageLogRepository);
    }

    @Test
    void latePaymentForCancelledSubscriptionDoesNotActivateIt() {
        SubscriptionPurchase purchase = SubscriptionPurchase.builder().id(UUID.randomUUID())
                .user(testUser).amount(new BigDecimal("50000")).status("CANCELLED").build();
        Payment payment = new Payment();
        payment.setSubscriptionPurchase(purchase);
        payment.setGateway("PAYOS");
        payment.setTransactionId("901");
        payment.setAmount(purchase.getAmount());
        payment.setStatus("CANCELLED");
        PaymentRepository.PaymentTarget target = mock(PaymentRepository.PaymentTarget.class);
        when(target.getSubscriptionPurchaseId()).thenReturn(purchase.getId());
        when(paymentRepository.findTargetByTransactionId("901")).thenReturn(Optional.of(target));
        when(paymentRepository.findByTransactionId("901")).thenReturn(Optional.of(payment));
        when(subscriptionPurchaseRepository.findLockedById(purchase.getId())).thenReturn(Optional.of(purchase));
        var gateway = stubPayosLinks();
        PaymentLink live = paidPayosLink(901L, 50000L);
        when(gateway.get(901L)).thenReturn(live);
        Webhook webhook = stubWebhook(901L, 50000L);

        paymentService.handleWebhook(webhook);
        paymentService.verify("901", testUser.getId());

        assertEquals(Payment.PENDING_RECONCILIATION, payment.getStatus());
        assertEquals("LATE_PAYMENT", payment.getReconciliationReason());
        assertEquals(new BigDecimal("50000"), payment.getReconciliationAmount());
        assertNull(payment.getPaidAt());
        assertNull(purchase.getSettledAt());
        assertEquals("CANCELLED", purchase.getStatus());
        verifyNoInteractions(userSubscriptionService, notificationService, usageLogRepository);
        verify(paymentRepository).save(payment);
    }

    @Test
    void codCheckoutCompletesWithoutAReceiptUntilAdminConfirmsAndChildReadsParentReceipt() {
        CheckoutOrder checkout = quotedCheckout("MARKETPLACE", "20000");
        checkout.setStatus("SHIPPING");
        checkout.setPaymentMethod("COD");
        checkout.setTotalAmount(new BigDecimal("140000"));
        Order child = new Order();
        child.setId(UUID.randomUUID());
        child.setCheckoutOrder(checkout);
        child.setStatus("SHIPPING");
        when(checkoutOrderRepository.findLockedById(checkout.getId())).thenReturn(Optional.of(checkout));
        when(orderRepository.findByCheckoutOrderId(checkout.getId())).thenReturn(List.of(child));
        when(orderRepository.findById(child.getId())).thenReturn(Optional.of(child));
        when(orderRepository.findCheckoutId(child.getId())).thenReturn(Optional.of(checkout.getId()));
        var proofService = new PaymentEvidenceServiceImpl(paymentRepository, checkoutOrderRepository, orderRepository,
                customOrderRepository, Clock.systemUTC(), benefitService);
        ReflectionTestUtils.setField(checkoutOrderService, "paymentEvidenceService", proofService);
        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setRole(UserRole.ADMIN);

        checkoutOrderService.updateAdminCheckoutOrderStatus(admin, checkout.getId(),
                AdminCheckoutOrderStatusRequestDTO.builder().status("COMPLETED").build());
        assertEquals("COMPLETED", checkout.getStatus());
        assertTrue(proofService.findSettledEvidence(PaymentEvidenceService.SourceType.ORDER, child.getId()).isEmpty());
        verify(paymentRepository, never()).save(any());

        UUID receiptId = UUID.randomUUID();
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment receipt = invocation.getArgument(0);
            receipt.setId(receiptId);
            when(paymentRepository.findByCheckoutOrderId(checkout.getId())).thenReturn(Optional.of(receipt));
            return receipt;
        });
        var request = AdminCheckoutOrderStatusRequestDTO.builder().status("COMPLETED")
                .codCollectedAmount(new BigDecimal("140000")).build();
        var confirmed = checkoutOrderService.updateAdminCheckoutOrderStatus(admin, checkout.getId(), request);
        var replay = proofService.recordCodCollection(PaymentEvidenceService.SourceType.ORDER, child.getId(),
                new BigDecimal("140000"), admin);
        assertEquals("SETTLED", confirmed.paymentEvidence().status());
        assertEquals(receiptId, replay.paymentId());
        assertEquals(PaymentEvidenceService.SourceType.CHECKOUT_ORDER, replay.sourceType());
        assertEquals(replay, proofService.findSettledEvidence(PaymentEvidenceService.SourceType.ORDER, child.getId()).orElseThrow());
        verify(paymentRepository).save(any(Payment.class));
        verify(paymentRepository, never()).findByOrderId(child.getId());
    }

    @Test
    void standaloneCodRejectsWrongAmountAndReturnsSameReceiptOnReplay() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus("COMPLETED");
        order.setPaymentMethod("COD");
        order.setTotalAmount(new BigDecimal("140000"));
        when(orderRepository.findLockedById(order.getId())).thenReturn(Optional.of(order));
        var service = new PaymentEvidenceServiceImpl(paymentRepository, checkoutOrderRepository,
                orderRepository, customOrderRepository, Clock.systemUTC(), benefitService);
        User admin = new User();
        admin.setRole(UserRole.ADMIN);
        assertThrows(ApiException.class, () -> service.recordCodCollection(PaymentEvidenceService.SourceType.ORDER,
                order.getId(), new BigDecimal("120000"), admin));
        verify(paymentRepository, never()).save(any());

        UUID receiptId = UUID.randomUUID();
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment receipt = invocation.getArgument(0);
            receipt.setId(receiptId);
            when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.of(receipt));
            return receipt;
        });
        PaymentEvidenceService.PaymentEvidence receipt = service.recordCodCollection(PaymentEvidenceService.SourceType.ORDER,
                order.getId(), new BigDecimal("140000"), admin);
        assertEquals(receiptId, receipt.paymentId());
        assertEquals(receipt, service.recordCodCollection(PaymentEvidenceService.SourceType.ORDER,
                order.getId(), new BigDecimal("140000"), admin));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void receiptForCheckoutChildIsFlaggedWithoutReplacingParentEvidence() {
        Payment parent = stubCheckoutPayment("951", "90000");
        var gateway = stubPayosLinks();
        PaymentLink parentLink = paidPayosLink(951L, 90000L);
        PaymentLink childLink = paidPayosLink(952L, 90000L);
        when(gateway.get(951L)).thenReturn(parentLink);
        when(gateway.get(952L)).thenReturn(childLink);
        paymentService.verify("951", testUser.getId());
        parent.setId(UUID.randomUUID());
        var checkout = parent.getCheckoutOrder();
        Order child = new Order();
        child.setId(UUID.randomUUID());
        child.setBuyer(testUser);
        child.setCheckoutOrder(checkout);
        child.setStatus("PAID");
        when(orderRepository.findLockedById(child.getId())).thenReturn(Optional.of(child));
        when(orderRepository.findById(child.getId())).thenReturn(Optional.of(child));
        when(paymentRepository.findByCheckoutOrderId(checkout.getId())).thenReturn(Optional.of(parent));

        Payment extra = new Payment();
        extra.setOrder(child);
        extra.setGateway("PAYOS");
        extra.setTransactionId("952");
        extra.setStatus("PENDING");
        extra.setAmount(new BigDecimal("90000"));
        PaymentRepository.PaymentTarget target = mock(PaymentRepository.PaymentTarget.class);
        when(target.getOrderId()).thenReturn(child.getId());
        when(target.getParentCheckoutOrderId()).thenReturn(checkout.getId());
        when(paymentRepository.findTargetByTransactionId("952")).thenReturn(Optional.of(target));
        when(paymentRepository.findByTransactionId("952")).thenReturn(Optional.of(extra));
        var verified = paymentService.verify("952", testUser.getId());

        assertEquals("DUPLICATE_CHECKOUT_SOURCE", verified.get("reconciliationReason"));
        assertEquals(new BigDecimal("90000"), extra.getReconciliationAmount());
        assertEquals(Payment.PENDING_RECONCILIATION, extra.getStatus());
        assertEquals("SUCCESS", parent.getStatus());
        var service = new PaymentEvidenceServiceImpl(paymentRepository, checkoutOrderRepository,
                orderRepository, customOrderRepository, Clock.systemUTC(), benefitService);
        assertEquals(parent.getId(), service.findSettledEvidence(
                PaymentEvidenceService.SourceType.ORDER, child.getId()).orElseThrow().paymentId());
        verify(notificationService).sendNotification(eq(testUser), anyString(), anyString(), eq("PAYMENT"), anyString());
    }

    private Payment stubCheckoutPayment(String code, String amount) {
        CheckoutOrder checkout = quotedCheckout("CUSTOM", "30000");
        checkout.setStatus("CONFIRMED");
        checkout.setPaymentMethod("PAYOS");
        checkout.setTotalAmount(new BigDecimal(amount));
        when(checkoutOrderRepository.findLockedById(checkout.getId())).thenReturn(Optional.of(checkout));
        Payment payment = new Payment();
        payment.setCheckoutOrder(checkout);
        payment.setStatus("PENDING");
        payment.setGateway("PAYOS");
        payment.setTransactionId(code);
        payment.setAmount(checkout.getTotalAmount());
        PaymentRepository.PaymentTarget target = mock(PaymentRepository.PaymentTarget.class);
        when(target.getCheckoutOrderId()).thenReturn(checkout.getId());
        when(paymentRepository.findTargetByTransactionId(code)).thenReturn(Optional.of(target));
        when(paymentRepository.findByTransactionId(code)).thenReturn(Optional.of(payment));
        return payment;
    }

    private PaymentRequestsService stubPayosLinks() {
        PaymentRequestsService gateway = mock(PaymentRequestsService.class);
        when(payOS.paymentRequests()).thenReturn(gateway);
        return gateway;
    }

    private PaymentLink paidPayosLink(long code, long received) {
        PaymentLink live = mock(PaymentLink.class);
        when(live.getStatus()).thenReturn(PaymentLinkStatus.PAID);
        when(live.getOrderCode()).thenReturn(code);
        when(live.getAmountPaid()).thenReturn(received);
        Transaction tx = mock(Transaction.class);
        when(tx.getTransactionDateTime()).thenReturn("2026-11-19 10:15:00");
        when(live.getTransactions()).thenReturn(List.of(tx));
        return live;
    }

    private Webhook stubWebhook(long code, long amount) {
        when(paymentRepository.existsByTransactionId(Long.toString(code))).thenReturn(true);
        WebhooksService webhooks = mock(WebhooksService.class);
        when(payOS.webhooks()).thenReturn(webhooks);
        Webhook webhook = mock(Webhook.class);
        WebhookData data = mock(WebhookData.class);
        when(data.getOrderCode()).thenReturn(code);
        when(data.getCode()).thenReturn("00");
        when(data.getCurrency()).thenReturn("VND");
        when(data.getAmount()).thenReturn(amount);
        when(webhooks.verify(webhook)).thenReturn(data);
        return webhook;
    }

    @Test
    void returnsFreshQuoteInHttp409WithoutHoldingQuota() {
        CheckoutOrder checkout = stubQuotedCustomCheckout();
        CustomOrder design = customLine("QUOTED", "120000");
        when(customOrderRepository.findByCheckoutOrderId(checkout.getId())).thenReturn(List.of(design));
        checkout.setSummaryVersion(3L);
        stubFreeShippingPlan();

        ApiException error = assertThrows(ApiException.class, () -> checkoutOrderService.confirmCheckoutOrder(
                testUser, checkout.getId(), new CheckoutOrderConfirmRequestDTO(2L, "PAYOS", new BigDecimal("90000"))));

        var response = new GlobalExceptionHandler().handleApiException(error, new MockHttpServletRequest());
        assertEquals(409, response.getStatusCode().value());
        assertEquals(409, response.getBody().getCode());
        assertEquals("checkout.version_conflict", response.getBody().getErrorKey());
        Map<?, ?> result = (Map<?, ?>) response.getBody().getResult();
        assertEquals(3L, result.get("summaryVersion"));
        assertEquals(new BigDecimal("108000"), ((PricingBreakdownDTO) result.get("pricingBreakdown")).totalPayable());
        verify(userSubscriptionRepository, never()).findLockedActiveSubscriptionsByUser(any());
        verifyNoInteractions(usageLogRepository, mockPaymentService);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void zeroAmountCheckoutSettlesOnceAndCompletesWithoutAnyPayment() {
        CheckoutOrder checkout = stubQuotedCustomCheckout();
        CustomOrder design = customLine("QUOTED", "100000");
        design.setCheckoutOrder(checkout);
        when(customOrderRepository.findByCheckoutOrderId(checkout.getId())).thenReturn(List.of(design));
        when(customOrderRepository.findById(design.getId())).thenReturn(Optional.of(design));
        UserSubscription subscription = stubFullPromotion("CUSTOM");
        List<SubscriptionUsageLog> usage = trackBenefitUsage("CHECKOUT_ORDER", checkout.getId());
        var request = new CheckoutOrderConfirmRequestDTO(2L, "PAYOS", BigDecimal.ZERO);

        var estimate = checkoutOrderService.getCheckoutOrder(testUser, checkout.getId()).pricingBreakdown();
        assertEquals(BigDecimal.ZERO, estimate.totalPayable());
        assertFalse(estimate.zeroAmountSettlement());
        assertEquals(0, subscription.getFreeShippingUsesUsed());

        var confirmed = checkoutOrderService.confirmCheckoutOrder(testUser, checkout.getId(), request);
        var replay = checkoutOrderService.confirmCheckoutOrder(testUser, checkout.getId(), request);
        assertEquals("PAID", confirmed.status());
        assertEquals("PAID", design.getStatus());
        assertEquals(confirmed.paymentEvidence(), replay.paymentEvidence());
        assertEquals(confirmed.paymentEvidence(), confirmed.customOrderDetails().getFirst().paymentEvidence());
        assertEquals("ZERO_AMOUNT_PROMO", replay.paymentEvidence().status());
        assertEquals(checkout.getZeroAmountSettledAt(), replay.paymentEvidence().paidAt());
        assertTrue(replay.pricingBreakdown().zeroAmountSettlement());
        assertEquals(BigDecimal.ZERO, replay.totalAmount());
        assertNull(replay.paymentLinkUrl());
        assertNull(checkout.getPaymentExpiresAt());
        assertPromoAppliedOnce(subscription, usage, "CHECKOUT_ORDER", checkout.getId());

        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setRole(UserRole.ADMIN);
        for (String status : List.of("PREPARING", "PRINTING", "SHIPPING", "COMPLETED")) {
            var action = AdminCheckoutOrderStatusRequestDTO.builder().status(status).build();
            assertEquals(status, checkoutOrderService.updateAdminCheckoutOrderStatus(admin, checkout.getId(), action).status());
        }
        assertEquals("COMPLETED", design.getStatus());

        var evidenceService = new PaymentEvidenceServiceImpl(paymentRepository, checkoutOrderRepository,
                orderRepository, customOrderRepository, java.time.Clock.systemUTC(), benefitService);
        var evidence = evidenceService.findSettledEvidence(PaymentEvidenceService.SourceType.CUSTOM_ORDER, design.getId()).orElseThrow();
        assertEquals(PaymentEvidenceService.SourceType.CHECKOUT_ORDER, evidence.sourceType());
        assertEquals(checkout.getId(), evidence.sourceId());
        assertEquals(checkout.getZeroAmountSettledAt(), evidence.paidAt());
        assertTrue(evidence.zeroAmountPromo());
        assertNull(evidence.paymentId());
        assertEquals(evidence, evidenceService.recordCodCollection(
                PaymentEvidenceService.SourceType.CHECKOUT_ORDER, checkout.getId(), BigDecimal.ZERO, admin));

        var link = CreatePaymentLinkRequestDTO.builder().orderId(checkout.getId()).orderType("CHECKOUT_ORDER").build();
        assertThrows(ResponseStatusException.class, () -> paymentService.createPaymentLink(testUser.getId(), link));
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(payOS);
    }

    @Test
    void standaloneZeroAmountOrderCanBeReviewedAndEvaluatesZeroPoints() {
        UserSubscription subscription = stubFullPromotion("CUSTOM");
        CustomOrder custom = customLine("QUOTED", "100000");
        custom.setQuoteVersion(3L);
        when(customOrderRepository.findLockedById(custom.getId())).thenReturn(Optional.of(custom));
        when(customOrderRepository.findById(custom.getId())).thenReturn(Optional.of(custom));
        var usage = trackBenefitUsage("CUSTOM_ORDER", custom.getId());
        var service = new CustomOrderServiceImpl(customOrderRepository, null, paymentRepository,
                null, notificationService, null, new OrderTransitionPolicy(), null, null,
                checkoutOrderRepository, orderRepository, storeSettingRepository, benefitService);
        var request = CustomOrderStatusRequestDTO.builder().status("ACCEPTED").paymentMethod("PAYOS")
                .quoteVersion(3L).expectedTotalPayable(BigDecimal.ZERO).build();

        service.updateCustomOrderStatus(custom.getId(), request, testUser);
        var firstSettlement = custom.getZeroAmountSettledAt();
        service.updateCustomOrderStatus(custom.getId(), request, testUser);
        var detail = service.getCustomOrderDetail(custom.getId(), testUser);
        assertEquals("PAID", detail.status());
        assertEquals("ZERO_AMOUNT_PROMO", detail.paymentEvidence().status());
        assertEquals(firstSettlement, detail.paymentEvidence().paidAt());
        assertTrue(detail.pricingBreakdown().zeroAmountSettlement());
        assertPromoAppliedOnce(subscription, usage, "CUSTOM_ORDER", custom.getId());

        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setRole(UserRole.ADMIN);
        for (String status : List.of("PREPARING", "PRINTING", "SHIPPING", "COMPLETED")) {
            var action = CustomOrderStatusRequestDTO.builder().status(status).build();
            service.updateCustomOrderStatus(custom.getId(), action, admin);
        }
        assertEquals("COMPLETED", custom.getStatus());
        assertEquals(0, service.completeRewards(custom.getId()).rewardPointsEarned());

        var evidenceService = new PaymentEvidenceServiceImpl(paymentRepository, checkoutOrderRepository,
                orderRepository, customOrderRepository, java.time.Clock.systemUTC(), benefitService);
        var evidence = evidenceService.findSettledEvidence(PaymentEvidenceService.SourceType.CUSTOM_ORDER, custom.getId())
                .orElseThrow();
        assertNull(evidence.paymentId());
        assertTrue(evidence.zeroAmountPromo());
        assertEquals(firstSettlement, evidence.paidAt());

        OrderRewardRecordRepository rewards = mock(OrderRewardRecordRepository.class);
        PointWalletRepository wallets = mock(PointWalletRepository.class);
        PointTransactionRepository points = mock(PointTransactionRepository.class);
        PointWalletLockHelper walletLock = mock(PointWalletLockHelper.class);
        when(walletLock.lockOrCreateWallet(eq(testUser), any())).thenReturn(PointWallet.builder().balance(20).build());
        var rewardService = new OrderRewardServiceImpl(checkoutOrderRepository, orderRepository,
                customOrderRepository, rewards, wallets, points, walletLock, evidenceService, null);
        var reward = rewardService.processCompletionReward(PaymentEvidenceService.SourceType.CUSTOM_ORDER, custom.getId());
        assertEquals(OrderRewardStatus.EVALUATED_ZERO, reward.status());
        verifyNoInteractions(points, wallets);

        CustomOrderReviewRepository reviews = mock(CustomOrderReviewRepository.class);
        when(reviews.save(any(CustomOrderReview.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var reviewService = new CustomOrderReviewServiceImpl(customOrderRepository, reviews, null);
        var review = reviewService.upsertCustomOrderReview(custom.getId(),
                new CustomOrderReviewRequestDTO((short) 5, "In đẹp", List.of()), testUser);
        assertEquals((short) 5, review.rating());

        when(customOrderRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(custom));
        var dashboard = new DashboardServiceImpl(mock(OrderService.class), service, mock(DisputeRepository.class),
                userRepository).getDashboard(admin, true);
        assertEquals(BigDecimal.ZERO, dashboard.totalRevenue());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(payOS);
    }

    @Test
    void lostFreeShippingRequiresReconfirmationInsteadOfSettlingZero() {
        CheckoutOrder checkout = stubQuotedCustomCheckout();
        UserSubscription subscription = stubFullPromotion("CUSTOM");
        subscription.setFreeShippingUsesUsed(1);

        ApiException error = assertThrows(ApiException.class, () -> checkoutOrderService.confirmCheckoutOrder(
                testUser, checkout.getId(), new CheckoutOrderConfirmRequestDTO(2L, "PAYOS", BigDecimal.ZERO)));
        assertEquals("checkout.price_changed", error.getErrorCode().getErrorKey());
        var pricing = (PricingBreakdownDTO) ((Map<?, ?>) error.getResult()).get("pricingBreakdown");
        assertEquals(new BigDecimal("30000"), pricing.totalPayable());
        assertNull(checkout.getZeroAmountSettledAt());
        assertEquals("QUOTED", checkout.getStatus());
        verify(usageLogRepository, never()).saveAll(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void marketplaceCheckoutAppliesZeroPromotionAndConfirmsInventoryOnCreation() {
        UUID checkoutId = UUID.randomUUID();
        UUID childOrderId = UUID.randomUUID();
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setPrice(new BigDecimal("100000"));
        product.setSeller(testUser);
        CartItem line = new CartItem();
        line.setId(1L);
        line.setClientLineId(UUID.randomUUID());
        line.setCart(testCart);
        line.setQuantity(1);
        line.setProduct(product);
        when(cartRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(testCart.getId())).thenReturn(List.of(line));
        when(checkoutOrderRepository.save(any(CheckoutOrder.class))).thenAnswer(invocation -> {
            CheckoutOrder checkout = invocation.getArgument(0);
            checkout.setId(checkoutId);
            return checkout;
        });
        List<Order> children = new ArrayList<>();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(childOrderId);
            children.add(order);
            return order;
        });
        when(orderRepository.findByCheckoutOrderId(checkoutId)).thenReturn(children);
        UserSubscription subscription = stubFullPromotion("MARKETPLACE");
        var usage = trackBenefitUsage("CHECKOUT_ORDER", checkoutId);

        var request = CheckoutOrderCreateRequestDTO.builder()
                .selectedLineIds(List.of(line.getClientLineId().toString()))
                .paymentMethod("COD").shippingAddress("Địa chỉ kiểm thử").build();
        var response = checkoutOrderService.createCheckoutOrder(testUser, request);

        assertEquals("PAID", response.status());
        assertEquals("ZERO_AMOUNT_PROMO", response.paymentEvidence().status());
        assertTrue(response.pricingBreakdown().zeroAmountSettlement());
        assertEquals(BigDecimal.ZERO, response.totalAmount());
        assertEquals("PAID", children.getFirst().getStatus());
        assertEquals(BigDecimal.ZERO, children.getFirst().getTotalAmount());
        assertPromoAppliedOnce(subscription, usage, "CHECKOUT_ORDER", checkoutId);
        verify(mockInventoryService).reserveForOrder(childOrderId, Map.of(product.getId(), 1),
                "CHECKOUT_ORDER_" + checkoutId, null);
        verify(mockInventoryService).confirmReservation(childOrderId);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(payOS);
    }

    private UserSubscription stubFullPromotion(String scope) {
        UserSubscription subscription = stubBenefitPlan("""
                {"discount":{"percent":100,"appliesTo":["%s"]},
                 "shipping":{"freeUsesPerTerm":1,"maxDiscountAmountPerUse":30000,"appliesTo":["%s"]}}
                """.formatted(scope, scope));
        when(userSubscriptionRepository.findLockedActiveSubscriptionsByUser(testUser)).thenReturn(List.of(subscription));
        return subscription;
    }

    private List<SubscriptionUsageLog> trackBenefitUsage(String type, UUID id) {
        List<SubscriptionUsageLog> usage = new ArrayList<>();
        when(usageLogRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<SubscriptionUsageLog> entries = invocation.getArgument(0);
            entries.forEach(entry -> {
                if (!usage.contains(entry)) {
                    usage.add(entry);
                }
            });
            return usage;
        });
        when(usageLogRepository.findByTargetTypeAndTargetIdAndStatus(type, id, "RESERVED"))
                .thenAnswer(invocation -> usage.stream().filter(entry -> "RESERVED".equals(entry.getStatus())).toList());
        return usage;
    }

    private void assertPromoAppliedOnce(UserSubscription subscription, List<SubscriptionUsageLog> usage,
                                        String type, UUID id) {
        assertEquals(1, subscription.getFreeShippingUsesUsed());
        assertEquals(2, usage.size());
        assertTrue(usage.stream().allMatch(entry -> "APPLIED".equals(entry.getStatus())));
        verify(usageLogRepository).findByTargetTypeAndTargetIdAndStatus(type, id, "RESERVED");
    }

    @Test
    void exhaustedQuotaRequiresNewApprovalBeforeAnyWrites() {
        UserSubscription subscription = stubFreeShippingPlan();
        CheckoutOrder first = stubQuotedCustomCheckout();
        CheckoutOrder checkout = stubQuotedCustomCheckout();
        checkoutOrderService.confirmCheckoutOrder(testUser, first.getId(),
                new CheckoutOrderConfirmRequestDTO(2L, "PAYOS", new BigDecimal("90000")));
        clearInvocations(usageLogRepository, checkoutOrderRepository, paymentRepository);

        ApiException error = assertThrows(ApiException.class, () -> checkoutOrderService.confirmCheckoutOrder(
                testUser, checkout.getId(), new CheckoutOrderConfirmRequestDTO(2L, "PAYOS", new BigDecimal("90000"))));

        assertEquals("checkout.price_changed", error.getErrorCode().getErrorKey());
        Map<?, ?> result = (Map<?, ?>) error.getResult();
        var updatedPrice = (PricingBreakdownDTO) result.get("pricingBreakdown");
        assertEquals(BenefitStatus.QUOTA_EXHAUSTED, updatedPrice.benefitStatus());
        assertEquals(new BigDecimal("120000"), updatedPrice.totalPayable());
        assertEquals("QUOTED", checkout.getStatus());
        assertEquals(1, subscription.getFreeShippingUsesUsed());
        verify(usageLogRepository, never()).saveAll(any());
        verify(checkoutOrderRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());

        var accepted = checkoutOrderService.confirmCheckoutOrder(
                testUser, checkout.getId(), new CheckoutOrderConfirmRequestDTO(2L, "PAYOS", new BigDecimal("120000")));
        assertEquals(new BigDecimal("120000"), accepted.pricingBreakdown().totalPayable());
        assertEquals(1, subscription.getFreeShippingUsesUsed());
    }

    @Test
    void repeatedConfirmationAndPaymentLinkReuseLockedPriceAndCode() {
        CheckoutOrder checkout = stubQuotedCustomCheckout();
        UserSubscription subscription = stubFreeShippingPlan();
        var request = new CheckoutOrderConfirmRequestDTO(2L, "PAYOS", new BigDecimal("90000"));

        var confirmation = checkoutOrderService.confirmCheckoutOrder(testUser, checkout.getId(), request);
        assertEquals(PricingStage.LOCKED, confirmation.pricingBreakdown().pricingStage());
        assertEquals(new BigDecimal("90000"), confirmation.totalAmount());
        assertEquals(2L, confirmation.summaryVersion());

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment pending = paymentCaptor.getValue();
        when(paymentRepository.findByCheckoutOrderId(checkout.getId())).thenReturn(Optional.of(pending));

        PaymentRequestsService gateway = mock(PaymentRequestsService.class);
        when(payOS.paymentRequests()).thenReturn(gateway);
        CreatePaymentLinkResponse created = mock(CreatePaymentLinkResponse.class);
        when(created.getCheckoutUrl()).thenReturn("https://example.invalid/c1");
        when(gateway.create(any(CreatePaymentLinkRequest.class))).thenReturn(created);

        var linkRequest = CreatePaymentLinkRequestDTO.builder()
                .orderId(checkout.getId()).orderType("CHECKOUT_ORDER").build();
        var firstLink = paymentService.createPaymentLink(testUser.getId(), linkRequest);
        var repeated = checkoutOrderService.confirmCheckoutOrder(testUser, checkout.getId(), request);
        assertEquals(firstLink.paymentLinkUrl(), repeated.paymentLinkUrl());
        assertEquals(firstLink, paymentService.createPaymentLink(testUser.getId(), linkRequest));

        ArgumentCaptor<CreatePaymentLinkRequest> gatewayRequest = ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
        verify(gateway).create(gatewayRequest.capture());
        assertEquals(90000L, gatewayRequest.getValue().getAmount());
        assertEquals(pending.getTransactionId(), String.valueOf(gatewayRequest.getValue().getOrderCode()));
        assertEquals(1, subscription.getFreeShippingUsesUsed());
        verify(usageLogRepository).saveAll(any());
    }

    @Test
    void standaloneAcceptanceLocksQuoteForFuturePaymentRequests() {
        stubFreeShippingPlan();
        CustomOrder custom = customLine("QUOTED", "100000");
        custom.setQuoteVersion(5L);
        when(customOrderRepository.findLockedById(custom.getId())).thenReturn(Optional.of(custom));
        when(customOrderRepository.findById(custom.getId())).thenReturn(Optional.of(custom));
        CustomOrderService customService = new CustomOrderServiceImpl(customOrderRepository, null, paymentRepository,
                null, notificationService, null, new OrderTransitionPolicy(), null, null,
                checkoutOrderRepository, orderRepository, storeSettingRepository, benefitService);
        var request = CustomOrderStatusRequestDTO.builder().status("ACCEPTED").paymentMethod("PAYOS")
                .quoteVersion(5L).expectedTotalPayable(new BigDecimal("90000")).build();

        customService.updateCustomOrderStatus(custom.getId(), request, testUser);
        customService.updateCustomOrderStatus(custom.getId(), request, testUser);
        var pricing = customService.getCustomOrderDetail(custom.getId(), testUser).pricingBreakdown();
        assertEquals(PricingStage.LOCKED, pricing.pricingStage());
        assertEquals(new BigDecimal("90000"), pricing.totalPayable());
        ArgumentCaptor<Payment> confirmationPayment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(confirmationPayment.capture());
        assertEquals(custom, confirmationPayment.getValue().getCustomOrder());
        assertEquals(new BigDecimal("90000"), confirmationPayment.getValue().getAmount());
        verify(usageLogRepository).saveAll(any());
    }

    @Test
    void recoversSamePayosCodeWhenCreateResponseIsLost() {
        CheckoutOrder checkout = stubQuotedCustomCheckout();
        stubFreeShippingPlan();
        checkoutOrderService.confirmCheckoutOrder(testUser, checkout.getId(),
                new CheckoutOrderConfirmRequestDTO(2L, "PAYOS", new BigDecimal("90000")));
        ArgumentCaptor<Payment> pendingCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(pendingCaptor.capture());
        Payment pending = pendingCaptor.getValue();
        long code = Long.parseLong(pending.getTransactionId());
        when(paymentRepository.findByCheckoutOrderId(checkout.getId())).thenReturn(Optional.of(pending));
        PaymentRequestsService gateway = mock(PaymentRequestsService.class);
        when(payOS.paymentRequests()).thenReturn(gateway);
        when(gateway.create(any(CreatePaymentLinkRequest.class))).thenThrow(new IllegalStateException("timeout"));
        PaymentLink live = mockPaymentLink(PaymentLinkStatus.PENDING);
        when(live.getOrderCode()).thenReturn(code);
        when(live.getAmount()).thenReturn(90000L);
        when(live.getId()).thenReturn("124c33293c934a85be5b7f8761a27a07");
        when(gateway.get(code)).thenReturn(live);

        var linkRequest = CreatePaymentLinkRequestDTO.builder()
                .orderId(checkout.getId()).orderType("CHECKOUT_ORDER").build();
        var recovered = paymentService.createPaymentLink(testUser.getId(), linkRequest);
        assertEquals("https://pay.payos.vn/web/124c33293c934a85be5b7f8761a27a07", recovered.paymentLinkUrl());
        assertEquals(Long.toString(code), recovered.orderCode());
        assertEquals(recovered, paymentService.createPaymentLink(testUser.getId(), linkRequest));
        verify(gateway).create(any(CreatePaymentLinkRequest.class));
    }

    @Test
    void expiryReleasesFreeShippingOnlyOnce() {
        CheckoutOrder checkout = stubQuotedCustomCheckout();
        UserSubscription subscription = stubFreeShippingPlan();
        checkoutOrderService.confirmCheckoutOrder(testUser, checkout.getId(),
                new CheckoutOrderConfirmRequestDTO(2L, "PAYOS", new BigDecimal("90000")));
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment payment = paymentCaptor.getValue();
        payment.setExpiresAt(Instant.now().minusSeconds(10));
        String code = payment.getTransactionId();
        PaymentRepository.PaymentTarget source = mock(PaymentRepository.PaymentTarget.class);
        when(source.getCheckoutOrderId()).thenReturn(checkout.getId());
        when(paymentRepository.findTargetByTransactionId(code)).thenReturn(Optional.of(source));
        when(paymentRepository.findByTransactionId(code)).thenReturn(Optional.of(payment));

        SubscriptionUsageLog shipping = SubscriptionUsageLog.builder().userSubscription(subscription)
                .benefitType("FREE_SHIPPING").status("RESERVED").amountSaved(new BigDecimal("30000")).build();
        when(usageLogRepository.findByTargetTypeAndTargetIdAndStatus("CHECKOUT_ORDER", checkout.getId(), "RESERVED"))
                .thenReturn(List.of(shipping));
        when(userSubscriptionRepository.findLockedByIds(Set.of(subscription.getId()))).thenReturn(List.of(subscription));

        PaymentRequestsService gateway = mock(PaymentRequestsService.class);
        when(payOS.paymentRequests()).thenReturn(gateway);
        PaymentLink expired = mockPaymentLink(PaymentLinkStatus.EXPIRED);
        when(gateway.get(Long.valueOf(code))).thenReturn(expired);

        paymentService.expireQuotePayment(code);
        paymentService.expireQuotePayment(code);

        assertEquals("EXPIRED", payment.getStatus());
        assertEquals("EXPIRED", checkout.getStatus());
        assertEquals("RELEASED", shipping.getStatus());
        assertEquals(0, subscription.getFreeShippingUsesUsed());
        verify(gateway).get(Long.valueOf(code));
        verify(usageLogRepository).findByTargetTypeAndTargetIdAndStatus("CHECKOUT_ORDER", checkout.getId(), "RESERVED");
    }

    @Test
    void cancelledStandalonePaymentReleasesQuotaAndCannotCreateAnotherLink() {
        CustomOrder custom = customLine("ACCEPTED", "100000");
        Payment payment = new Payment();
        payment.setCustomOrder(custom);
        payment.setTransactionId("12345");
        payment.setGateway("PAYOS");
        payment.setStatus("PENDING");
        payment.setAmount(new BigDecimal("90000"));
        when(customOrderRepository.findLockedById(custom.getId())).thenReturn(Optional.of(custom));
        PaymentRepository.PaymentTarget source = mock(PaymentRepository.PaymentTarget.class);
        when(source.getCustomOrderId()).thenReturn(custom.getId());
        when(paymentRepository.findTargetByTransactionId("12345")).thenReturn(Optional.of(source));
        when(paymentRepository.findByTransactionId("12345")).thenReturn(Optional.of(payment));

        UserSubscription subscription = stubFreeShippingPlan();
        subscription.setFreeShippingUsesUsed(1);
        var shipping = SubscriptionUsageLog.builder().userSubscription(subscription)
                .benefitType("FREE_SHIPPING").status("RESERVED").amountSaved(new BigDecimal("30000")).build();
        when(usageLogRepository.findByTargetTypeAndTargetIdAndStatus("CUSTOM_ORDER", custom.getId(), "RESERVED"))
                .thenReturn(List.of(shipping));
        when(userSubscriptionRepository.findLockedByIds(Set.of(subscription.getId()))).thenReturn(List.of(subscription));
        PaymentRequestsService gateway = mock(PaymentRequestsService.class);
        when(payOS.paymentRequests()).thenReturn(gateway);
        PaymentLink cancelled = mockPaymentLink(PaymentLinkStatus.CANCELLED);
        when(cancelled.getOrderCode()).thenReturn(12345L);
        when(gateway.get(12345L)).thenReturn(cancelled);

        paymentService.verify("12345", testUser.getId());
        paymentService.verify("12345", testUser.getId());
        assertEquals("CANCELLED", custom.getStatus());
        assertEquals("CANCELLED", payment.getStatus());
        assertEquals(0, subscription.getFreeShippingUsesUsed());
        verify(usageLogRepository).findByTargetTypeAndTargetIdAndStatus("CUSTOM_ORDER", custom.getId(), "RESERVED");
        var request = CreatePaymentLinkRequestDTO.builder().orderId(custom.getId()).orderType("CUSTOM_ORDER").build();
        assertThrows(ResponseStatusException.class, () -> paymentService.createPaymentLink(testUser.getId(), request));
        verify(gateway, never()).create(any(CreatePaymentLinkRequest.class));
    }

    private PaymentLink mockPaymentLink(PaymentLinkStatus status) {
        PaymentLink link = mock(PaymentLink.class);
        when(link.getStatus()).thenReturn(status);
        when(link.getAmountPaid()).thenReturn(0L);
        return link;
    }

    private CheckoutOrder stubQuotedCustomCheckout() {
        CheckoutOrder checkout = quotedCheckout("CUSTOM", "30000");
        checkout.setPaymentMethod("PAYOS");
        when(checkoutOrderRepository.findLockedById(checkout.getId())).thenReturn(Optional.of(checkout));
        when(customOrderRepository.findByCheckoutOrderId(checkout.getId()))
                .thenReturn(List.of(customLine("QUOTED", "100000")));
        return checkout;
    }

    private UserSubscription stubFreeShippingPlan() {
        UserSubscription subscription = stubBenefitPlan("""
                {"discount":{"percent":10,"appliesTo":["CUSTOM"]},
                 "shipping":{"freeUsesPerTerm":1,"maxDiscountAmountPerUse":30000,"appliesTo":["CUSTOM"]}}
                """);
        when(userSubscriptionRepository.findLockedActiveSubscriptionsByUser(testUser)).thenReturn(List.of(subscription));
        return subscription;
    }

    @Test
    void previewsMixedCheckoutFromActiveLinesWithoutReservingBenefits() {
        UserSubscription subscription = stubBenefitPlan("""
                {"discount":{"percent":10,"appliesTo":["CUSTOM"]}}
                """);
        CheckoutOrder checkout = quotedCheckout("MIXED", "30000");
        CustomOrder custom = customLine("QUOTED", "100000");
        CustomOrder cancelled = customLine("CANCELLED", "50000");
        Order market = new Order();
        market.setSubtotalAmount(new BigDecimal("200000"));
        market.setTotalAmount(new BigDecimal("230000"));
        when(customOrderRepository.findByCheckoutOrderId(checkout.getId())).thenReturn(List.of(custom, cancelled));
        when(orderRepository.findByCheckoutOrderId(checkout.getId())).thenReturn(List.of(market));
        when(checkoutOrderRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId())).thenReturn(List.of(checkout));

        var price = checkoutOrderService.getCheckoutOrder(testUser, checkout.getId()).pricingBreakdown();
        var listPrice = checkoutOrderService.getMyCheckoutOrders(testUser).getFirst().pricingBreakdown();

        assertEquals(price, listPrice);
        assertEquals(PricingStage.ESTIMATED, price.pricingStage());
        assertEquals(new BigDecimal("100000"), price.customItemsSubtotal());
        assertEquals(new BigDecimal("200000"), price.marketplaceItemsSubtotal());
        assertEquals(new BigDecimal("10000"), price.itemDiscountAmount());
        assertEquals(new BigDecimal("320000"), price.totalPayable());
        assertEquals(0, subscription.getFreeShippingUsesUsed());
        verifyNoInteractions(usageLogRepository);
        verify(userSubscriptionRepository, never()).save(any());
    }

    @Test
    void previewsFullCustomBenefitsAndExplainsExhaustedShippingQuota() {
        UserSubscription subscription = stubBenefitPlan("""
                {"discount":{"percent":10,"appliesTo":["CUSTOM"]},
                 "shipping":{"freeUsesPerTerm":1,"maxDiscountAmountPerUse":30000,"appliesTo":["CUSTOM"]}}
                """);
        CheckoutOrder checkout = quotedCheckout("CUSTOM", "30000");
        when(customOrderRepository.findByCheckoutOrderId(checkout.getId()))
                .thenReturn(List.of(customLine("QUOTED", "100000")));

        var estimate = checkoutOrderService.getCheckoutOrder(testUser, checkout.getId()).pricingBreakdown();
        assertEquals(new BigDecimal("90000"), estimate.totalPayable());
        assertEquals(BenefitStatus.APPLIED, estimate.benefitStatus());
        assertEquals(0, subscription.getFreeShippingUsesUsed());

        subscription.setFreeShippingUsesUsed(1);
        var noFreeShipping = checkoutOrderService.getCheckoutOrder(testUser, checkout.getId()).pricingBreakdown();
        assertEquals(new BigDecimal("120000"), noFreeShipping.totalPayable());
        assertEquals(BenefitStatus.QUOTA_EXHAUSTED, noFreeShipping.benefitStatus());
        assertNotNull(noFreeShipping.ineligibleReason());
        verifyNoInteractions(usageLogRepository);
    }

    @Test
    void explainsMissingAndExpiredBenefitConfiguration() {
        stubBenefitPlan(null);
        CheckoutOrder checkout = quotedCheckout("CUSTOM", "0");
        when(customOrderRepository.findByCheckoutOrderId(checkout.getId()))
                .thenReturn(List.of(customLine("QUOTED", "100000")));

        var incomplete = checkoutOrderService.getCheckoutOrder(testUser, checkout.getId()).pricingBreakdown();
        assertEquals(BenefitStatus.CONFIG_REQUIRED, incomplete.benefitStatus());
        assertEquals(new BigDecimal("100000"), incomplete.totalPayable());
        assertEquals(BigDecimal.ZERO, incomplete.baseShippingFee());
        assertNotNull(incomplete.ineligibleReason());

        when(userSubscriptionRepository.findActiveSubscriptionsByUser(testUser)).thenReturn(List.of());
        when(userSubscriptionRepository.hasExpiredSubscription(testUser)).thenReturn(true);
        var expired = checkoutOrderService.getCheckoutOrder(testUser, checkout.getId()).pricingBreakdown();
        assertEquals(BenefitStatus.EXPIRED, expired.benefitStatus());
        verifyNoInteractions(usageLogRepository);
    }

    @Test
    void previewsCustomDiscountAtMinimumAndCapWithZeroShipping() {
        stubBenefitPlan("""
                {"discount":{"percent":10,"appliesTo":["CUSTOM"],
                             "minEligibleSubtotal":100000,"maxDiscountAmount":12000}}
                """);
        CheckoutOrder checkout = quotedCheckout("CUSTOM", "0");
        CustomOrder custom = customLine("QUOTED", "99999");
        when(customOrderRepository.findByCheckoutOrderId(checkout.getId())).thenReturn(List.of(custom));

        var belowMinimum = checkoutOrderService.getCheckoutOrder(testUser, checkout.getId()).pricingBreakdown();
        assertEquals(BenefitStatus.INELIGIBLE, belowMinimum.benefitStatus());
        assertEquals(new BigDecimal("99999"), belowMinimum.totalPayable());
        assertNotNull(belowMinimum.ineligibleReason());

        custom.setQuotedPrice(new BigDecimal("100000"));
        var atMinimum = checkoutOrderService.getCheckoutOrder(testUser, checkout.getId()).pricingBreakdown();
        assertEquals(new BigDecimal("90000"), atMinimum.totalPayable());

        custom.setQuotedPrice(new BigDecimal("200000"));
        var capped = checkoutOrderService.getCheckoutOrder(testUser, checkout.getId()).pricingBreakdown();
        assertEquals(new BigDecimal("12000"), capped.itemDiscountAmount());
        assertEquals(new BigDecimal("188000"), capped.totalPayable());
        verifyNoInteractions(usageLogRepository);
    }

    @Test
    void versionsStandaloneQuotesOnMeaningfulChangesAndPreservesZeroShipping() {
        StoreSetting shipping = new StoreSetting();
        shipping.setValue("0");
        when(storeSettingRepository.findById("shippingFee")).thenReturn(Optional.of(shipping));
        CustomOrder custom = customLine("REQUESTED", null);
        custom.setQuantity(2);
        when(customOrderRepository.findLockedById(custom.getId())).thenReturn(Optional.of(custom));
        when(customOrderRepository.findById(custom.getId())).thenReturn(Optional.of(custom));
        CustomOrderService customService = new CustomOrderServiceImpl(customOrderRepository, null, paymentRepository,
                null, notificationService, null, new OrderTransitionPolicy(), null, null,
                checkoutOrderRepository, orderRepository, storeSettingRepository, benefitService);

        var waiting = customService.getCustomOrderDetail(custom.getId(), testUser).pricingBreakdown();
        assertEquals(PricingStage.PENDING_QUOTE, waiting.pricingStage());
        assertNull(waiting.totalPayable());

        customService.quoteCustomOrder(custom.getId(), new CustomOrderQuoteRequestDTO(new BigDecimal("50000")), testUser);
        var quoted = customService.getCustomOrderDetail(custom.getId(), testUser);
        assertEquals(2L, quoted.quoteVersion());
        assertEquals(BigDecimal.ZERO, quoted.pricingBreakdown().baseShippingFee());
        assertEquals(new BigDecimal("100000"), quoted.pricingBreakdown().totalPayable());

        customService.quoteCustomOrder(custom.getId(), new CustomOrderQuoteRequestDTO(new BigDecimal("50000.00")), testUser);
        assertEquals(2L, custom.getQuoteVersion());
        customService.quoteCustomOrder(custom.getId(),
                new CustomOrderQuoteRequestDTO(new BigDecimal("50000"), "PLA+"), testUser);
        assertEquals(3L, custom.getQuoteVersion());
    }

    @Test
    void invalidBatchQuotesLeaveBothLinesAndVersionUntouched() {
        CheckoutOrder checkout = quotedCheckout("CUSTOM", "0");
        checkout.setStatus("PENDING_QUOTE");
        CustomOrder first = customLine("REQUESTED", null);
        first.setQuantity(2);
        CustomOrder second = customLine("REQUESTED", null);
        when(checkoutOrderRepository.findLockedById(checkout.getId())).thenReturn(Optional.of(checkout));
        when(customOrderRepository.findByCheckoutOrderId(checkout.getId())).thenReturn(List.of(first, second));
        ItemQuote firstQuote = new ItemQuote(first.getId(), new BigDecimal("50000"), null);
        ItemQuote invalidPrice = new ItemQuote(second.getId(), BigDecimal.ZERO, null);
        for (var invalid : List.of(List.of(firstQuote), List.of(firstQuote, firstQuote), List.of(firstQuote, invalidPrice))) {
            assertThrows(ApiException.class, () -> checkoutOrderService.batchQuoteCheckoutOrder(
                    testUser, checkout.getId(), new CheckoutOrderBatchQuoteRequestDTO(invalid)));
        }

        assertEquals(2L, checkout.getSummaryVersion());
        assertEquals("PENDING_QUOTE", checkout.getStatus());
        assertNull(first.getQuotedPrice());
        assertNull(second.getQuotedPrice());
        verify(customOrderRepository, never()).save(any());
        verify(checkoutOrderRepository, never()).save(any());

        ItemQuote secondQuote = new ItemQuote(second.getId(), new BigDecimal("25000"), null);
        var result = checkoutOrderService.batchQuoteCheckoutOrder(
                testUser, checkout.getId(), new CheckoutOrderBatchQuoteRequestDTO(List.of(firstQuote, secondQuote)));
        assertEquals(3L, result.summaryVersion());
        assertEquals(new BigDecimal("125000"), result.pricingBreakdown().itemsSubtotal());
        assertEquals(new BigDecimal("155000"), result.pricingBreakdown().totalPayable());
    }

    private CheckoutOrder quotedCheckout(String orderType, String shippingFee) {
        CheckoutOrder checkout = new CheckoutOrder();
        checkout.setId(UUID.randomUUID());
        checkout.setUser(testUser);
        checkout.setOrderType(orderType);
        checkout.setStatus("QUOTED");
        checkout.setSummaryVersion(2L);
        checkout.setShippingFee(new BigDecimal(shippingFee));
        when(checkoutOrderRepository.findById(checkout.getId())).thenReturn(Optional.of(checkout));
        return checkout;
    }

    private CustomOrder customLine(String status, String quotedPrice) {
        CustomOrder custom = new CustomOrder();
        custom.setId(UUID.randomUUID());
        custom.setBuyer(testUser);
        custom.setQuantity(1);
        custom.setStatus(status);
        if (quotedPrice != null) {
            custom.setQuotedPrice(new BigDecimal(quotedPrice));
        }
        return custom;
    }

    private UserSubscription stubBenefitPlan(String structuredBenefits) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName("G1");
        plan.setStructuredBenefits(structuredBenefits);
        UserSubscription subscription = new UserSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setPlan(plan);
        subscription.setFreeShippingUsesUsed(0);
        when(userSubscriptionRepository.findActiveSubscriptionsByUser(testUser)).thenReturn(List.of(subscription));
        return subscription;
    }

    @Test
    @DisplayName("FLOW_01.1: Cart service must strip client-supplied customPrice to null")
    void testUpdateCart_StripsClientCustomPrice() {
        when(cartRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(testCart));

        CartItemRequestDTO customLine = CartItemRequestDTO.builder()
                .clientLineId(UUID.randomUUID())
                .quantity(2)
                .customDesign("{\"model\":\"ruler-20cm\"}")
                .customName("Thước 20cm Khắc Tên")
                .customPrice(new BigDecimal("1000")) // Client attempts to supply custom price
                .build();

        CartUpdateRequestDTO updateRequest = CartUpdateRequestDTO.builder()
                .items(List.of(customLine))
                .build();

        cartService.updateCart(testUser, updateRequest);

        ArgumentCaptor<CartItem> itemCaptor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository, times(1)).save(itemCaptor.capture());

        CartItem savedItem = itemCaptor.getValue();
        assertNull(savedItem.getCustomPrice(), "Custom price from client must be stripped to null on server");
        assertEquals("Thước 20cm Khắc Tên", savedItem.getCustomName());
        assertEquals(2, savedItem.getQuantity());
    }

    @Test
    @DisplayName("FLOW_01.2: Checkout order with custom items must be PENDING_QUOTE, quotedPrice=null, status=REQUESTED")
    void testCreateCheckoutOrder_WithCustomLine_StatusIsPendingQuote() {
        when(cartRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(testCart));

        UUID clientLineId = UUID.randomUUID();
        CartItem customItem = new CartItem();
        customItem.setId(101L);
        customItem.setClientLineId(clientLineId);
        customItem.setCart(testCart);
        customItem.setQuantity(1);
        customItem.setCustomDesign("{\"specs\":\"custom\"}");
        customItem.setCustomName("Thước Eke Cá Nhân");
        customItem.setCustomPrice(null);

        when(cartItemRepository.findByCartIdOrderByIdAsc(testCart.getId())).thenReturn(List.of(customItem));
        when(checkoutOrderRepository.save(any(CheckoutOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutOrderCreateRequestDTO request = CheckoutOrderCreateRequestDTO.builder()
                .selectedLineIds(List.of(clientLineId.toString()))
                .shippingAddress("Làng Đại Học TP.HCM")
                .paymentMethod("PAYOS")
                .shippingMode("COMMON")
                .build();

        checkoutOrderService.createCheckoutOrder(testUser, request);

        // Verify CheckoutOrder status is PENDING_QUOTE
        ArgumentCaptor<CheckoutOrder> orderCaptor = ArgumentCaptor.forClass(CheckoutOrder.class);
        verify(checkoutOrderRepository, atLeastOnce()).save(orderCaptor.capture());
        CheckoutOrder savedOrder = orderCaptor.getValue();

        assertEquals("PENDING_QUOTE", savedOrder.getStatus(), "Order containing custom lines must be PENDING_QUOTE");

        // Verify CustomOrder has quotedPrice=null and status=REQUESTED
        ArgumentCaptor<CustomOrder> customCaptor = ArgumentCaptor.forClass(CustomOrder.class);
        verify(customOrderRepository, times(1)).save(customCaptor.capture());
        CustomOrder savedCustom = customCaptor.getValue();

        assertNull(savedCustom.getQuotedPrice(), "Custom order quoted price must be null before admin review");
        assertEquals("REQUESTED", savedCustom.getStatus(), "Custom order status must strictly be REQUESTED");
    }

    @Test
    @DisplayName("FLOW_01.3: Payment link creation must reject PENDING_QUOTE checkout orders")
    void testCreatePaymentLink_PendingQuoteCheckoutOrder_ThrowsException() {
        CheckoutOrder pendingOrder = new CheckoutOrder();
        pendingOrder.setId(UUID.randomUUID());
        pendingOrder.setUser(testUser);
        pendingOrder.setStatus("PENDING_QUOTE");
        pendingOrder.setTotalAmount(new BigDecimal("150000"));

        when(checkoutOrderRepository.findLockedById(pendingOrder.getId())).thenReturn(Optional.of(pendingOrder));

        CreatePaymentLinkRequestDTO request = CreatePaymentLinkRequestDTO.builder()
                .orderId(pendingOrder.getId())
                .orderType("CHECKOUT_ORDER")
                .build();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            paymentService.createPaymentLink(testUser.getId(), request);
        });

        assertTrue(exception.getReason().contains("không ở trạng thái có thể thanh toán"));
    }

    @Test
    @DisplayName("FLOW_01.4: Payment link creation must reject checkout orders with unaccepted custom orders")
    void testCreatePaymentLink_WithUnacceptedCustomOrder_ThrowsException() {
        CheckoutOrder confirmedOrder = new CheckoutOrder();
        confirmedOrder.setId(UUID.randomUUID());
        confirmedOrder.setUser(testUser);
        confirmedOrder.setStatus("CONFIRMED");
        confirmedOrder.setTotalAmount(new BigDecimal("150000"));

        CustomOrder unacceptedCustom = new CustomOrder();
        unacceptedCustom.setId(UUID.randomUUID());
        unacceptedCustom.setStatus("REQUESTED"); // Not accepted yet!

        when(checkoutOrderRepository.findLockedById(confirmedOrder.getId())).thenReturn(Optional.of(confirmedOrder));
        when(customOrderRepository.findByCheckoutOrderId(confirmedOrder.getId())).thenReturn(List.of(unacceptedCustom));

        CreatePaymentLinkRequestDTO request = CreatePaymentLinkRequestDTO.builder()
                .orderId(confirmedOrder.getId())
                .orderType("CHECKOUT_ORDER")
                .build();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            paymentService.createPaymentLink(testUser.getId(), request);
        });

        assertTrue(exception.getReason().contains("chưa được duyệt báo giá"));
    }

    @Test
    @DisplayName("FLOW_01.5: Settle payment must guard child orders and not mark unaccepted customs as PAID")
    void testSettle_DoesNotMarkUnacceptedCustomsAsPaid() throws Exception {
        CheckoutOrder checkoutOrder = new CheckoutOrder();
        checkoutOrder.setId(UUID.randomUUID());
        checkoutOrder.setUser(testUser);
        checkoutOrder.setStatus("CONFIRMED");

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setCheckoutOrder(checkoutOrder);
        payment.setStatus("PENDING");
        payment.setAmount(new BigDecimal("150000"));
        payment.setTransactionId("123456");

        // Child custom 1: Already accepted and quoted
        CustomOrder acceptedCustom = new CustomOrder();
        acceptedCustom.setId(UUID.randomUUID());
        acceptedCustom.setStatus("ACCEPTED");
        acceptedCustom.setQuotedPrice(new BigDecimal("120000"));

        // Child custom 2: Still requested / unquoted
        CustomOrder requestedCustom = new CustomOrder();
        requestedCustom.setId(UUID.randomUUID());
        requestedCustom.setStatus("REQUESTED");
        requestedCustom.setQuotedPrice(null);

        // Child order 3: Cancelled
        Order cancelledOrder = new Order();
        cancelledOrder.setId(UUID.randomUUID());
        cancelledOrder.setStatus("CANCELLED");

        when(checkoutOrderRepository.save(any(CheckoutOrder.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findByCheckoutOrderId(checkoutOrder.getId())).thenReturn(List.of(cancelledOrder));
        when(customOrderRepository.findByCheckoutOrderId(checkoutOrder.getId())).thenReturn(List.of(acceptedCustom, requestedCustom));

        // Invoke private settle(Payment payment, BigDecimal amount) via reflection
        Method settleMethod = PaymentServiceImpl.class.getDeclaredMethod("settle", Payment.class, BigDecimal.class, Instant.class, boolean.class);
        settleMethod.setAccessible(true);
        settleMethod.invoke(paymentService, payment, new BigDecimal("150000"), Instant.now(), true);

        // Assertions
        assertEquals("PAID", checkoutOrder.getStatus());
        assertEquals("PAID", acceptedCustom.getStatus(), "ACCEPTED custom order with quoted price must be PAID");
        assertEquals("REQUESTED", requestedCustom.getStatus(), "Unquoted/REQUESTED custom order must NOT be marked PAID");
        assertEquals("CANCELLED", cancelledOrder.getStatus(), "CANCELLED order must NOT be marked PAID");

        verify(customOrderRepository, times(1)).save(acceptedCustom);
        verify(customOrderRepository, never()).save(requestedCustom);
        verify(orderRepository, never()).save(cancelledOrder);
    }
}
