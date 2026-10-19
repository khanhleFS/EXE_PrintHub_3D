package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderConfirmRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderResponseDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.custom_prints.CustomOrderStatusRequestDTO;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.impl.CheckoutOrderServiceImpl;
import com.fpt.printhub_3d.service.impl.CustomOrderServiceImpl;
import com.fpt.printhub_3d.service.impl.PaymentAttemptHelper;
import com.fpt.printhub_3d.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CheckoutOrderSafetyTest {

    @Mock private CheckoutOrderRepository checkoutOrderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private CustomOrderRepository customOrderRepository;
    @Mock private PaymentService paymentService;
    @Mock private InventoryReservationRepository inventoryReservationRepository;
    @Mock private IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock private StoreSettingRepository storeSettingRepository;
    @Mock private UserSubscriptionRepository userSubscriptionRepository;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;

    @Mock private PaymentRepository paymentRepository;
    @Mock private NotificationService notificationService;
    @Mock private UserRepository userRepository;
    @Mock private FileAssetRepository fileAssetRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private vn.payos.PayOS payOS;
    @Mock private PointWalletRepository pointWalletRepository;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private com.fpt.printhub_3d.service.impl.CheckoutRewardService checkoutRewardService;

    private CheckoutOrderServiceImpl checkoutOrderService;
    private CustomOrderServiceImpl customOrderService;
    private PaymentServiceImpl paymentServiceImpl;

    private User buyer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        checkoutOrderService = new CheckoutOrderServiceImpl(
                checkoutOrderRepository,
                cartRepository,
                cartItemRepository,
                productRepository,
                orderRepository,
                orderItemRepository,
                customOrderRepository,
                paymentService,
                inventoryReservationRepository,
                idempotencyRecordRepository,
                storeSettingRepository,
                userSubscriptionRepository,
                paymentRepository
        );

        customOrderService = new CustomOrderServiceImpl(
                customOrderRepository,
                fileAssetRepository,
                paymentRepository,
                userRepository,
                fileStorageService,
                notificationService,
                cloudinaryService,
                checkoutOrderRepository,
                pointWalletRepository,
                pointTransactionRepository,
                orderRepository,
                checkoutRewardService
        );

        PaymentAttemptHelper paymentAttemptHelper = new PaymentAttemptHelper(paymentRepository, paymentAttemptRepository);
        com.fpt.printhub_3d.service.impl.PaymentTransactionStepService paymentTransactionStepService =
                new com.fpt.printhub_3d.service.impl.PaymentTransactionStepService(
                        orderRepository,
                        customOrderRepository,
                        checkoutOrderRepository,
                        paymentRepository,
                        paymentAttemptRepository,
                        inventoryReservationRepository,
                        productRepository,
                        notificationService
                );

        paymentServiceImpl = new PaymentServiceImpl(
                orderRepository,
                customOrderRepository,
                checkoutOrderRepository,
                paymentRepository,
                payOS,
                notificationService,
                inventoryReservationRepository,
                productRepository,
                paymentAttemptRepository,
                paymentAttemptHelper,
                paymentTransactionStepService
        );

        buyer = User.builder()
                .id(UUID.randomUUID())
                .fullName("Nguyễn Văn A")
                .role(UserRole.USER)
                .build();
    }

    @Test
    @DisplayName("PRICE-01: Custom line with client customPrice is NEVER auto-quoted, status is PENDING_QUOTE")
    void testPrice01_CustomLine_NeverAutoQuoted() {
        UUID cartId = UUID.randomUUID();
        Cart cart = new Cart();
        cart.setId(cartId);
        cart.setUser(buyer);

        UUID clientLineId = UUID.randomUUID();
        CartItem customItem = new CartItem();
        customItem.setId(101L);
        customItem.setClientLineId(clientLineId);
        customItem.setCart(cart);
        customItem.setCustomDesign("{\"shape\":\"ruler\",\"length\":20}");
        customItem.setCustomName("Thước test");
        customItem.setCustomPrice(BigDecimal.valueOf(1000)); // Malicious client price
        customItem.setQuantity(2);

        when(cartRepository.findByUserId(buyer.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(cartId)).thenReturn(List.of(customItem));
        when(checkoutOrderRepository.save(any(CheckoutOrder.class))).thenAnswer(inv -> {
            CheckoutOrder o = inv.getArgument(0);
            if (o.getId() == null) o.setId(UUID.randomUUID());
            return o;
        });

        CheckoutOrderCreateRequestDTO request = CheckoutOrderCreateRequestDTO.builder()
                .selectedLineIds(List.of(clientLineId.toString()))
                .shippingAddress("123 Phố Huế, Hà Nội")
                .shippingMode("COMMON")
                .paymentMethod("PAYOS")
                .build();

        CheckoutOrderResponseDTO response = checkoutOrderService.createCheckoutOrder(buyer, request);

        assertNotNull(response);
        assertEquals("PENDING_QUOTE", response.status(), "Order containing custom MUST be PENDING_QUOTE");

        ArgumentCaptor<CustomOrder> captor = ArgumentCaptor.forClass(CustomOrder.class);
        verify(customOrderRepository).save(captor.capture());
        CustomOrder savedCustom = captor.getValue();
        assertNull(savedCustom.getQuotedPrice(), "CustomOrder quotedPrice MUST be null, cannot take client customPrice");
        assertEquals("REQUESTED", savedCustom.getStatus(), "CustomOrder status MUST be REQUESTED");
    }

    @Test
    @DisplayName("PAY-02: Creating CheckoutOrder with custom items rejects COD")
    void testPay02_CheckoutWithCustom_RejectsCOD() {
        UUID cartId = UUID.randomUUID();
        Cart cart = new Cart();
        cart.setId(cartId);
        cart.setUser(buyer);

        UUID clientLineId = UUID.randomUUID();
        CartItem customItem = new CartItem();
        customItem.setId(101L);
        customItem.setClientLineId(clientLineId);
        customItem.setCart(cart);
        customItem.setCustomDesign("{\"shape\":\"ruler\"}");
        customItem.setQuantity(1);

        when(cartRepository.findByUserId(buyer.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(cartId)).thenReturn(List.of(customItem));

        CheckoutOrderCreateRequestDTO request = CheckoutOrderCreateRequestDTO.builder()
                .selectedLineIds(List.of(clientLineId.toString()))
                .shippingAddress("123 Phố Huế, Hà Nội")
                .shippingMode("COMMON")
                .paymentMethod("COD")
                .build();

        ApiException ex = assertThrows(ApiException.class, () -> checkoutOrderService.createCheckoutOrder(buyer, request));
        assertTrue(ex.getMessage().contains("không hỗ trợ COD") || ex.getMessage().contains("PayOS"));
    }

    @Test
    @DisplayName("PAY-01: PaymentService blocks PayOS payment link creation for PENDING_QUOTE")
    void testPay01_PaymentService_BlocksPendingQuote() {
        UUID checkoutOrderId = UUID.randomUUID();
        CheckoutOrder order = new CheckoutOrder();
        order.setId(checkoutOrderId);
        order.setUser(buyer);
        order.setStatus("PENDING_QUOTE");
        order.setTotalAmount(BigDecimal.valueOf(150_000));

        when(checkoutOrdersMock().findLockedById(checkoutOrderId)).thenReturn(Optional.of(order));

        CreatePaymentLinkRequestDTO req = CreatePaymentLinkRequestDTO.builder()
                .orderId(checkoutOrderId)
                .orderType("CHECKOUT_ORDER")
                .paymentOption("FULL")
                .customAmount(BigDecimal.valueOf(150_000))
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentServiceImpl.createPaymentLink(buyer.getId(), req));
        assertTrue(ex.getReason().contains("CONFIRMED") || ex.getReason().contains("không ở trạng thái"));
    }

    @Test
    @DisplayName("ORDER-02: confirmCheckoutOrder is idempotent when already CONFIRMED")
    void testOrder02_ConfirmCheckoutOrder_IdempotentReentry() {
        UUID checkoutOrderId = UUID.randomUUID();
        CheckoutOrder order = new CheckoutOrder();
        order.setId(checkoutOrderId);
        order.setUser(buyer);
        order.setStatus("CONFIRMED");
        order.setSummaryVersion(2L);
        order.setTotalAmount(BigDecimal.valueOf(250_000));
        order.setPaymentMethod("PAYOS");

        when(checkoutOrderRepository.findLockedById(checkoutOrderId)).thenReturn(Optional.of(order));
        when(customOrderRepository.findByCheckoutOrderId(checkoutOrderId)).thenReturn(List.of());
        when(orderRepository.findByCheckoutOrderId(checkoutOrderId)).thenReturn(List.of());

        CheckoutOrderConfirmRequestDTO req = CheckoutOrderConfirmRequestDTO.builder()
                .summaryVersion(2L)
                .paymentMethod("PAYOS")
                .build();

        CheckoutOrderResponseDTO response = checkoutOrderService.confirmCheckoutOrder(buyer, checkoutOrderId, req);

        assertNotNull(response);
        assertEquals("CONFIRMED", response.status());
        // Verify inventory reservation was NOT called again
        verify(inventoryReservationRepository, never()).save(any(InventoryReservation.class));
    }

    @Test
    @DisplayName("16.3: CustomOrderService prohibits COD and forbids production before PAID")
    void test16_3_CustomOrderProhibitsCOD_AndEnforcesPaidBeforeProduction() {
        CustomOrderCreateRequestDTO createReq = CustomOrderCreateRequestDTO.builder()
                .attachmentUrl("https://test.com/file.stl")
                .requirements("Yêu cầu in 3D")
                .quantity(1)
                .shippingAddress("Hà Nội")
                .rulerModel("Thước kẻ")
                .paymentMethod("COD") // Prohibited COD
                .build();

        ApiException ex1 = assertThrows(ApiException.class, () -> customOrderService.createCustomOrder(buyer, createReq));
        assertTrue(ex1.getMessage().contains("không hỗ trợ COD") || ex1.getMessage().contains("PayOS"));

        // Test accepting quote with COD
        UUID customOrderId = UUID.randomUUID();
        CustomOrder customOrder = new CustomOrder();
        customOrder.setId(customOrderId);
        customOrder.setBuyer(buyer);
        customOrder.setStatus("QUOTED");
        customOrder.setQuotedPrice(BigDecimal.valueOf(50_000));

        when(customOrderRepository.findLockedById(customOrderId)).thenReturn(Optional.of(customOrder));

        CustomOrderStatusRequestDTO acceptWithCod = new CustomOrderStatusRequestDTO("ACCEPTED", "COD");
        ApiException ex2 = assertThrows(ApiException.class, () -> customOrderService.updateCustomOrderStatus(customOrderId, acceptWithCod, buyer));
        assertTrue(ex2.getMessage().contains("PayOS"));

        // Test transitioning to PREPARING directly from ACCEPTED (unpaid) is rejected
        User admin = User.builder().id(UUID.randomUUID()).fullName("Admin").role(UserRole.ADMIN).build();
        customOrder.setStatus("ACCEPTED");
        CustomOrderStatusRequestDTO startProduction = new CustomOrderStatusRequestDTO("PREPARING", null);
        ApiException ex3 = assertThrows(ApiException.class, () -> customOrderService.updateCustomOrderStatus(customOrderId, startProduction, admin));
        assertTrue(ex3.getMessage().contains("không hợp lệ"));
    }

    @Test
    @DisplayName("16.4: Block standalone payment when order/custom belongs to CheckoutOrder (L02)")
    void test16_4_BlockStandalonePayment_WhenBelongsToCheckoutOrder() {
        UUID orderId = UUID.randomUUID();
        CheckoutOrder parentCheckout = new CheckoutOrder();
        parentCheckout.setId(UUID.randomUUID());

        Order order = new Order();
        order.setId(orderId);
        order.setBuyer(buyer);
        order.setCheckoutOrder(parentCheckout);

        when(orderRepository.findLockedById(orderId)).thenReturn(Optional.of(order));

        CreatePaymentLinkRequestDTO orderReq = CreatePaymentLinkRequestDTO.builder()
                .orderId(orderId)
                .orderType("ORDER")
                .paymentOption("FULL")
                .build();
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class, () -> paymentServiceImpl.createPaymentLink(buyer.getId(), orderReq));
        assertTrue(ex1.getReason() != null && ex1.getReason().contains("CheckoutOrder"));

        UUID customId = UUID.randomUUID();
        CustomOrder customOrder = new CustomOrder();
        customOrder.setId(customId);
        customOrder.setBuyer(buyer);
        customOrder.setCheckoutOrder(parentCheckout);
        customOrder.setStatus("QUOTED");
        customOrder.setQuotedPrice(BigDecimal.valueOf(50_000));

        when(customOrderRepository.findLockedById(customId)).thenReturn(Optional.of(customOrder));

        CreatePaymentLinkRequestDTO customReq = CreatePaymentLinkRequestDTO.builder()
                .orderId(customId)
                .orderType("CUSTOM_ORDER")
                .paymentOption("FULL")
                .build();
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class, () -> paymentServiceImpl.createPaymentLink(buyer.getId(), customReq));
        assertTrue(ex2.getReason() != null && ex2.getReason().contains("CheckoutOrder"));
    }

    @Test
    @DisplayName("16.5: Settle flags RECONCILIATION_REQUIRED instead of clamping when stock is deficient (L01)")
    void test16_5_SettleFlagsReconciliationRequired_WhenStockDeficient() {
        UUID checkoutId = UUID.randomUUID();
        CheckoutOrder checkoutOrder = new CheckoutOrder();
        checkoutOrder.setId(checkoutId);
        checkoutOrder.setStatus("CONFIRMED");
        checkoutOrder.setShippingMode("COMMON");
        checkoutOrder.setTotalAmount(BigDecimal.valueOf(100_000));
        checkoutOrder.setSummaryVersion(1L);

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setCheckoutOrder(checkoutOrder);
        payment.setScope("ALL");
        payment.setStatus("PENDING");
        payment.setAmount(BigDecimal.valueOf(100_000));

        UUID prodId = UUID.randomUUID();
        Product product = new Product();
        product.setId(prodId);
        product.setTitle("Robot Toy");
        product.setStock(1); // Only 1 left!

        InventoryReservation reservation = new InventoryReservation();
        reservation.setId(UUID.randomUUID());
        reservation.setCheckoutOrder(checkoutOrder);
        reservation.setProduct(product);
        reservation.setQuantity(5); // Needs 5! Deficit!
        reservation.setStatus("RESERVED");
        reservation.setExpiresAt(Instant.now().plusSeconds(600));

        when(inventoryReservationRepository.findByCheckoutOrderId(checkoutId)).thenReturn(new ArrayList<>(List.of(reservation)));
        when(productRepository.findLockedById(prodId)).thenReturn(Optional.of(product));
        when(orderRepository.findByCheckoutOrderId(checkoutId)).thenReturn(Collections.emptyList());
        when(customOrderRepository.findByCheckoutOrderId(checkoutId)).thenReturn(Collections.emptyList());

        paymentServiceImpl.settle(payment, BigDecimal.valueOf(100_000));

        // Stock must NOT be reduced into negative numbers
        assertEquals(1, product.getStock());
        // Reservation must be flagged as RECONCILIATION_REQUIRED
        assertEquals("RECONCILIATION_REQUIRED", reservation.getStatus());
        verify(inventoryReservationRepository, atLeastOnce()).save(reservation);
    }

    private CheckoutOrderRepository checkoutOrdersMock() {
        return checkoutOrderRepository;
    }
}
