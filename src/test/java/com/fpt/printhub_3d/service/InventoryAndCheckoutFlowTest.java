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
                inventoryService
        );

        testOrderId = UUID.randomUUID();

        testProduct = new Product();
        testProduct.setId(UUID.randomUUID());
        testProduct.setTitle("Thước Kẻ Kỹ Thuật 20cm Cyber Neon");
        testProduct.setStock(10);
        testProduct.setPrice(BigDecimal.valueOf(50000));
        testProduct.setStatus("ACTIVE");

        testBuyer = new User();
        testBuyer.setId(UUID.randomUUID());
        testBuyer.setRole(UserRole.USER);
        testBuyer.setFullName("Nguyễn Văn A");
    }

    @Test
    @DisplayName("1. Giữ chỗ tồn kho (ACTIVE): trừ stock đúng 1 lần và ghi nhận InventoryMovement")
    void testReserveStockDecrementsProductStockAndCreatesActiveReservation() {
        when(productRepository.findLockedById(testProduct.getId())).thenReturn(Optional.of(testProduct));
        when(inventoryReservationRepository.findByOrderIdAndProductId(testOrderId, testProduct.getId())).thenReturn(Optional.empty());
        when(inventoryReservationRepository.save(any(InventoryReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<UUID, Integer> requestMap = Map.of(testProduct.getId(), 3);
        Instant expiresAt = Instant.now().plusSeconds(900);

        inventoryService.reserveForOrder(testOrderId, requestMap, "OP_TEST_1", expiresAt);

        // Kiểm tra tồn kho khả dụng giảm từ 10 xuống 7
        assertEquals(7, testProduct.getStock());
        verify(productRepository, times(1)).save(testProduct);

        // Kiểm tra tạo reservation ở trạng thái ACTIVE
        ArgumentCaptor<InventoryReservation> resCaptor = ArgumentCaptor.forClass(InventoryReservation.class);
        verify(inventoryReservationRepository).save(resCaptor.capture());
        InventoryReservation savedRes = resCaptor.getValue();
        assertEquals(ReservationState.ACTIVE, savedRes.getState());
        assertEquals(3, savedRes.getQuantity());

        // Kiểm tra tạo movement loại RESERVE delta = -3, availableAfter = 7
        ArgumentCaptor<InventoryMovement> moveCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository).save(moveCaptor.capture());
        InventoryMovement savedMove = moveCaptor.getValue();
        assertEquals(MovementType.RESERVE, savedMove.getOperationType());
        assertEquals(-3, savedMove.getDelta());
        assertEquals(7, savedMove.getAvailableAfter());
    }

    @Test
    @DisplayName("2. Kiểm tra chặn bán vượt (OUT_OF_STOCK) khi số lượng mua lớn hơn tồn kho")
    void testReserveStockThrowsOutOfStockWhenRequestedExceedsStock() {
        testProduct.setStock(2);
        when(productRepository.findLockedById(testProduct.getId())).thenReturn(Optional.of(testProduct));
        when(inventoryReservationRepository.findByOrderIdAndProductId(testOrderId, testProduct.getId())).thenReturn(Optional.empty());

        Map<UUID, Integer> requestMap = Map.of(testProduct.getId(), 5);

        ApiException ex = assertThrows(ApiException.class, () ->
                inventoryService.reserveForOrder(testOrderId, requestMap, "OP_TEST_2", null));

        assertEquals(OrderErrorCode.OUT_OF_STOCK, ex.getErrorCode());
        assertEquals(2, testProduct.getStock(), "Tồn kho không được thay đổi khi bị từ chối");
        verify(inventoryReservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. Xác nhận giữ chỗ (ACTIVE -> CONFIRMED): Không trừ tồn kho lần thứ hai")
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

        // Gọi confirm
        inventoryService.confirmReservation(testOrderId);

        // Reservation chuyển sang CONFIRMED
        assertEquals(ReservationState.CONFIRMED, activeRes.getState());
        assertNotNull(activeRes.getConfirmedAt());
        verify(inventoryReservationRepository, times(1)).save(activeRes);

        // Tuyệt đối không gọi sửa Product stock lần 2
        verify(productRepository, never()).findLockedById(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. Giải phóng tồn kho (ACTIVE -> RELEASED): Hoàn lại Product.stock đúng số lượng đã giữ")
    void testReleaseActiveReservationIncrementsProductStockAndCreatesReleasedMovement() {
        testProduct.setStock(7); // Giả sử trước đó đã bị trừ 3
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

        // Giải phóng giữ chỗ
        inventoryService.releaseActiveReservations(testOrderId, "Khách hàng hủy đơn PENDING", "OP_CANCEL_1");

        // Tồn kho được hoàn lại: 7 + 3 = 10
        assertEquals(10, testProduct.getStock());
        verify(productRepository, times(1)).save(testProduct);

        // Reservation chuyển sang RELEASED
        assertEquals(ReservationState.RELEASED, activeRes.getState());
        assertEquals("Khách hàng hủy đơn PENDING", activeRes.getReleaseReason());

        // Ghi nhận InventoryMovement loại RELEASE delta = +3, availableAfter = 10
        ArgumentCaptor<InventoryMovement> moveCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository).save(moveCaptor.capture());
        InventoryMovement savedMove = moveCaptor.getValue();
        assertEquals(MovementType.RELEASE, savedMove.getOperationType());
        assertEquals(3, savedMove.getDelta());
        assertEquals(10, savedMove.getAvailableAfter());
    }

    @Test
    @DisplayName("5. Hủy đơn hàng PENDING: Kích hoạt hoàn lại tồn kho trong OrderService")
    void testOrderCancellationReleasesActiveReservation() {
        Order pendingOrder = new Order();
        pendingOrder.setId(testOrderId);
        pendingOrder.setBuyer(testBuyer);
        pendingOrder.setStatus("PENDING");

        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(pendingOrder));
        when(paymentRepository.findByOrderId(testOrderId)).thenReturn(Optional.empty());

        // Giả lập reservation active đang có
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

        // Người mua thực hiện hủy đơn
        orderService.updateOrderStatus(testOrderId, "CANCELLED", testBuyer);

        // Kiểm tra đơn chuyển sang CANCELLED
        assertEquals("CANCELLED", pendingOrder.getStatus());
        verify(orderRepository).save(pendingOrder);

        // Kiểm tra stock được hoàn lại từ 8 lên 10
        assertEquals(10, testProduct.getStock());
        assertEquals(ReservationState.RELEASED, activeRes.getState());
    }
}
