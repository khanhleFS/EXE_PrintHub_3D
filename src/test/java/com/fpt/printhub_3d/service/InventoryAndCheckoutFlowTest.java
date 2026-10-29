package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.OrderErrorCode;
import com.fpt.printhub_3d.entity.Enumeration.MovementType;
import com.fpt.printhub_3d.entity.Enumeration.ReservationState;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.InventoryMovement;
import com.fpt.printhub_3d.entity.InventoryReservation;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Product;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.impl.InventoryServiceImpl;
import com.fpt.printhub_3d.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryAndCheckoutFlowTest {

    private InventoryServiceImpl inventoryService;
    private OrderServiceImpl orderService;

    @Mock private ProductRepository productRepository;
    @Mock private InventoryReservationRepository inventoryReservationRepository;
    @Mock private InventoryMovementRepository inventoryMovementRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ShippingInfoRepository shippingInfoRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PointWalletRepository pointWalletRepository;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private NotificationService notificationService;
    @Mock private ReviewRepository reviewRepository;
    @Mock private OrderTransitionPolicy orderTransitionPolicy;

    private Product testProduct;
    private UUID testOrderId;
    private User testBuyer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        inventoryService = new InventoryServiceImpl(
                productRepository,
                inventoryReservationRepository,
                inventoryMovementRepository
        );

        orderService = new OrderServiceImpl(
                productRepository,
                orderRepository,
                orderItemRepository,
                shippingInfoRepository,
                userRepository,
                paymentRepository,
                pointWalletRepository,
                pointTransactionRepository,
                notificationService,
                reviewRepository,
                inventoryService,
                orderTransitionPolicy
        );

        testOrderId = UUID.randomUUID();

        testProduct = new Product();
        testProduct.setId(UUID.randomUUID());
        testProduct.setTitle("ThÆ°á»›c Káº» Ká»¹ Thuáº­t 20cm Cyber Neon");
        testProduct.setStock(10);
        testProduct.setPrice(BigDecimal.valueOf(50000));
        testProduct.setStatus("ACTIVE");

        testBuyer = new User();
        testBuyer.setId(UUID.randomUUID());
        testBuyer.setRole(UserRole.USER);
        testBuyer.setFullName("Nguyá»…n VÄƒn A");
    }

    @Test
    @DisplayName("1. Giá»¯ chá»— tá»“n kho (ACTIVE): trá»« stock Ä‘Ãºng 1 láº§n vÃ  ghi nháº­n InventoryMovement")
    void testReserveStockDecrementsProductStockAndCreatesActiveReservation() {
        when(productRepository.findLockedById(testProduct.getId())).thenReturn(Optional.of(testProduct));
        when(inventoryReservationRepository.findByOrderIdAndProductId(testOrderId, testProduct.getId())).thenReturn(Optional.empty());
        when(inventoryReservationRepository.save(any(InventoryReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<UUID, Integer> requestMap = Map.of(testProduct.getId(), 3);
        Instant expiresAt = Instant.now().plusSeconds(900);

        inventoryService.reserveForOrder(testOrderId, requestMap, "OP_TEST_1", expiresAt);

        // Kiá»ƒm tra tá»“n kho kháº£ dá»¥ng giáº£m tá»« 10 xuá»‘ng 7
        assertEquals(7, testProduct.getStock());
        verify(productRepository, times(1)).save(testProduct);

        // Kiá»ƒm tra táº¡o reservation á»Ÿ tráº¡ng thÃ¡i ACTIVE
        ArgumentCaptor<InventoryReservation> resCaptor = ArgumentCaptor.forClass(InventoryReservation.class);
        verify(inventoryReservationRepository).save(resCaptor.capture());
        InventoryReservation savedRes = resCaptor.getValue();
        assertEquals(ReservationState.ACTIVE, savedRes.getState());
        assertEquals(3, savedRes.getQuantity());

        // Kiá»ƒm tra táº¡o movement loáº¡i RESERVE delta = -3, availableAfter = 7
        ArgumentCaptor<InventoryMovement> moveCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository).save(moveCaptor.capture());
        InventoryMovement savedMove = moveCaptor.getValue();
        assertEquals(MovementType.RESERVE, savedMove.getOperationType());
        assertEquals(-3, savedMove.getDelta());
        assertEquals(7, savedMove.getAvailableAfter());
    }

    @Test
    @DisplayName("2. Kiá»ƒm tra cháº·n bÃ¡n vÆ°á»£t (OUT_OF_STOCK) khi sá»‘ lÆ°á»£ng mua lá»›n hÆ¡n tá»“n kho")
    void testReserveStockThrowsOutOfStockWhenRequestedExceedsStock() {
        testProduct.setStock(2);
        when(productRepository.findLockedById(testProduct.getId())).thenReturn(Optional.of(testProduct));
        when(inventoryReservationRepository.findByOrderIdAndProductId(testOrderId, testProduct.getId())).thenReturn(Optional.empty());

        Map<UUID, Integer> requestMap = Map.of(testProduct.getId(), 5);

        ApiException ex = assertThrows(ApiException.class, () ->
                inventoryService.reserveForOrder(testOrderId, requestMap, "OP_TEST_2", null));

        assertEquals(OrderErrorCode.OUT_OF_STOCK, ex.getErrorCode());
        assertEquals(2, testProduct.getStock(), "Tá»“n kho khÃ´ng Ä‘Æ°á»£c thay Ä‘á»•i khi bá»‹ tá»« chá»‘i");
        verify(inventoryReservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. XÃ¡c nháº­n giá»¯ chá»— (ACTIVE -> CONFIRMED): KhÃ´ng trá»« tá»“n kho láº§n thá»© hai")
    void testConfirmReservationDoesNotDecrementStockAgain() {
        InventoryReservation activeRes = InventoryReservation.builder()
                .id(UUID.randomUUID())
                .orderId(testOrderId)
                .productId(testProduct.getId())
                .quantity(3)
                .state(ReservationState.ACTIVE)
                .reservedAt(Instant.now())
                .build();

        when(inventoryReservationRepository.findLockedByOrderId(testOrderId)).thenReturn(List.of(activeRes));

        // Gá»i confirm
        inventoryService.confirmReservation(testOrderId);

        // Reservation chuyá»ƒn sang CONFIRMED
        assertEquals(ReservationState.CONFIRMED, activeRes.getState());
        assertNotNull(activeRes.getConfirmedAt());
        verify(inventoryReservationRepository, times(1)).save(activeRes);

        // Tuyá»‡t Ä‘á»‘i khÃ´ng gá»i sá»­a Product stock láº§n 2
        verify(productRepository, never()).findLockedById(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. Giáº£i phÃ³ng tá»“n kho (ACTIVE -> RELEASED): HoÃ n láº¡i Product.stock Ä‘Ãºng sá»‘ lÆ°á»£ng Ä‘Ã£ giá»¯")
    void testReleaseActiveReservationIncrementsProductStockAndCreatesReleasedMovement() {
        testProduct.setStock(7); // Giáº£ sá»­ trÆ°á»›c Ä‘Ã³ Ä‘Ã£ bá»‹ trá»« 3
        InventoryReservation activeRes = InventoryReservation.builder()
                .id(UUID.randomUUID())
                .orderId(testOrderId)
                .productId(testProduct.getId())
                .quantity(3)
                .state(ReservationState.ACTIVE)
                .reservedAt(Instant.now())
                .build();

        when(inventoryReservationRepository.findLockedByOrderId(testOrderId)).thenReturn(List.of(activeRes));
        when(productRepository.findLockedById(testProduct.getId())).thenReturn(Optional.of(testProduct));

        // Giáº£i phÃ³ng giá»¯ chá»—
        inventoryService.releaseActiveReservations(testOrderId, "KhÃ¡ch hÃ ng há»§y Ä‘Æ¡n PENDING", "OP_CANCEL_1");

        // Tá»“n kho Ä‘Æ°á»£c hoÃ n láº¡i: 7 + 3 = 10
        assertEquals(10, testProduct.getStock());
        verify(productRepository, times(1)).save(testProduct);

        // Reservation chuyá»ƒn sang RELEASED
        assertEquals(ReservationState.RELEASED, activeRes.getState());
        assertEquals("KhÃ¡ch hÃ ng há»§y Ä‘Æ¡n PENDING", activeRes.getReleaseReason());

        // Ghi nháº­n InventoryMovement loáº¡i RELEASE delta = +3, availableAfter = 10
        ArgumentCaptor<InventoryMovement> moveCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository).save(moveCaptor.capture());
        InventoryMovement savedMove = moveCaptor.getValue();
        assertEquals(MovementType.RELEASE, savedMove.getOperationType());
        assertEquals(3, savedMove.getDelta());
        assertEquals(10, savedMove.getAvailableAfter());
    }

    @Test
    @DisplayName("5. Há»§y Ä‘Æ¡n hÃ ng PENDING: KÃ­ch hoáº¡t hoÃ n láº¡i tá»“n kho trong OrderService")
    void testOrderCancellationReleasesActiveReservation() {
        Order pendingOrder = new Order();
        pendingOrder.setId(testOrderId);
        pendingOrder.setBuyer(testBuyer);
        pendingOrder.setStatus("PENDING");

        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(pendingOrder));
        when(paymentRepository.findByOrderId(testOrderId)).thenReturn(Optional.empty());

        // Giáº£ láº­p reservation active Ä‘ang cÃ³
        InventoryReservation activeRes = InventoryReservation.builder()
                .id(UUID.randomUUID())
                .orderId(testOrderId)
                .productId(testProduct.getId())
                .quantity(2)
                .state(ReservationState.ACTIVE)
                .reservedAt(Instant.now())
                .build();

        testProduct.setStock(8);
        when(inventoryReservationRepository.findLockedByOrderId(testOrderId)).thenReturn(List.of(activeRes));
        when(productRepository.findLockedById(testProduct.getId())).thenReturn(Optional.of(testProduct));

        // NgÆ°á»i mua thá»±c hiá»‡n há»§y Ä‘Æ¡n
        when(orderTransitionPolicy.validateAndResolveNextStatus(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("CANCELLED");
        orderService.updateOrderStatus(testOrderId, "CANCELLED", testBuyer);

        // Kiá»ƒm tra Ä‘Æ¡n chuyá»ƒn sang CANCELLED
        assertEquals("CANCELLED", pendingOrder.getStatus());
        verify(orderRepository).save(pendingOrder);

        // Kiá»ƒm tra stock Ä‘Æ°á»£c hoÃ n láº¡i tá»« 8 lÃªn 10
        assertEquals(10, testProduct.getStock());
        assertEquals(ReservationState.RELEASED, activeRes.getState());
    }
}
