package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.cart.CartItemRequestDTO;
import com.fpt.printhub_3d.dto.cart.CartUpdateRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.impl.CartServiceImpl;
import com.fpt.printhub_3d.service.impl.CheckoutOrderServiceImpl;
import com.fpt.printhub_3d.service.impl.PaymentServiceImpl;
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

    // Services under test
    private CartServiceImpl cartService;
    private CheckoutOrderServiceImpl checkoutOrderService;
    private PaymentServiceImpl paymentService;

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
    @Mock private PayOS payOS;
    @Mock private NotificationService notificationService;
    @Mock private InventoryService mockInventoryService;

    private User testUser;
    private Cart testCart;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        cartService = new CartServiceImpl(cartRepository, cartItemRepository, productRepository, productService);

        checkoutOrderService = new CheckoutOrderServiceImpl(
                checkoutOrderRepository,
                cartRepository,
                cartItemRepository,
                productRepository,
                orderRepository,
                orderItemRepository,
                customOrderRepository,
                mockPaymentService,
                mockInventoryService
        );

        paymentService = new PaymentServiceImpl(
                orderRepository,
                customOrderRepository,
                checkoutOrderRepository,
                paymentRepository,
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
