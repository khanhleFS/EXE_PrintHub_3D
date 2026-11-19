package com.fpt.printhub_3d.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.dto.cart.CartItemRequestDTO;
import com.fpt.printhub_3d.dto.cart.CartUpdateRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderBatchQuoteRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderBatchQuoteRequestDTO.ItemQuote;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderQuoteRequestDTO;
import com.fpt.printhub_3d.dto.order.PricingBreakdownDTO.PricingStage;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.dto.subscription.BenefitPreviewResponseDTO.BenefitStatus;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.impl.CartServiceImpl;
import com.fpt.printhub_3d.service.impl.CheckoutOrderServiceImpl;
import com.fpt.printhub_3d.service.impl.CustomOrderServiceImpl;
import com.fpt.printhub_3d.service.impl.PaymentServiceImpl;
import com.fpt.printhub_3d.service.impl.SubscriptionBenefitServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;
import vn.payos.PayOS;

import java.lang.reflect.Method;
import java.math.BigDecimal;
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
                mockInventoryService
        );

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("student@fpt.edu.vn");

        testCart = new Cart();
        testCart.setId(UUID.randomUUID());
        testCart.setUser(testUser);
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
        Method settleMethod = PaymentServiceImpl.class.getDeclaredMethod("settle", Payment.class, BigDecimal.class);
        settleMethod.setAccessible(true);
        settleMethod.invoke(paymentService, payment, new BigDecimal("150000"));

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
