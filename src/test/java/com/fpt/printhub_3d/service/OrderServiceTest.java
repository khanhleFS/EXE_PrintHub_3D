package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.order.RewardCompletionResponseDTO;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.PointTransaction;
import com.fpt.printhub_3d.entity.PointWallet;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.impl.OrderServiceImpl;
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

class OrderServiceTest {

    private OrderService orderService;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ShippingInfoRepository shippingInfoRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PointWalletRepository pointWalletRepository;
    @Mock
    private PointTransactionRepository pointTransactionRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private InventoryReservationRepository inventoryReservationRepository;
    @Mock
    private CheckoutOrderRepository checkoutOrderRepository;
    @Mock
    private CustomOrderRepository customOrderRepository;
    @Mock
    private com.fpt.printhub_3d.service.impl.CheckoutRewardService checkoutRewardService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
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
                inventoryReservationRepository,
                checkoutOrderRepository,
                customOrderRepository,
                checkoutRewardService
        );
    }

    @Test
    void testCompleteRewards_WhenWalletDoesNotExist_CreatesWalletAndAwardsPoints() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();

        User buyer = User.builder()
                .id(buyerId)
                .fullName("Lê Quốc Khánh")
                .build();

        Order order = new Order();
        order.setId(orderId);
        order.setBuyer(buyer);
        order.setStatus("COMPLETED");
        order.setTotalAmount(BigDecimal.valueOf(100_000)); // 100k -> 10 points (unit: 10_000)
        order.setRewardProcessed(false);

        com.fpt.printhub_3d.entity.Payment mockPayment1 = new com.fpt.printhub_3d.entity.Payment();
        mockPayment1.setStatus("SUCCESS");
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(mockPayment1));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(pointWalletRepository.findById(buyerId)).thenReturn(Optional.empty());
        when(pointWalletRepository.save(any(PointWallet.class))).thenAnswer(inv -> inv.getArgument(0));

        RewardCompletionResponseDTO response = orderService.completeRewards(orderId);

        assertNotNull(response);
        assertEquals(orderId, response.orderId());
        assertEquals(buyerId, response.customerId());
        assertEquals(10, response.rewardPointsEarned());
        assertEquals(10, response.totalRewardPoints());
        assertFalse(response.alreadyProcessed());

        assertTrue(order.getRewardProcessed());
        assertEquals(10, order.getRewardPointsEarned());

        verify(pointWalletRepository, times(1)).save(any(PointWallet.class));
        verify(pointTransactionRepository, times(1)).save(any(PointTransaction.class));
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void testCompleteRewards_WhenWalletExists_IncreasesBalance() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();

        User buyer = User.builder()
                .id(buyerId)
                .fullName("Lê Quốc Khánh")
                .build();

        PointWallet existingWallet = PointWallet.builder()
                .userId(buyerId)
                .user(buyer)
                .balance(50)
                .isNew(false)
                .build();
        buyer.setPointWallet(existingWallet);

        Order order = new Order();
        order.setId(orderId);
        order.setBuyer(buyer);
        order.setStatus("COMPLETED");
        order.setTotalAmount(BigDecimal.valueOf(50_000)); // 50k -> 5 points
        order.setRewardProcessed(false);

        com.fpt.printhub_3d.entity.Payment mockPayment2 = new com.fpt.printhub_3d.entity.Payment();
        mockPayment2.setStatus("SUCCESS");
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(mockPayment2));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(pointWalletRepository.findById(buyerId)).thenReturn(Optional.of(existingWallet));
        when(pointWalletRepository.save(any(PointWallet.class))).thenAnswer(inv -> inv.getArgument(0));

        RewardCompletionResponseDTO response = orderService.completeRewards(orderId);

        assertNotNull(response);
        assertEquals(5, response.rewardPointsEarned());
        assertEquals(55, response.totalRewardPoints());
        assertEquals(55, existingWallet.getBalance());
        assertFalse(response.alreadyProcessed());

        verify(pointWalletRepository, times(1)).save(existingWallet);
        verify(pointTransactionRepository, times(1)).save(any(PointTransaction.class));
    }

    @Test
    void testCompleteRewards_ExcludesShippingFeeWhenItemsPresent() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();

        User buyer = User.builder()
                .id(buyerId)
                .fullName("Lê Quốc Khánh")
                .build();

        Order order = new Order();
        order.setId(orderId);
        order.setBuyer(buyer);
        order.setStatus("COMPLETED");
        // Total amount includes 15k shipping fee: 40k item + 15k ship = 55k
        order.setTotalAmount(BigDecimal.valueOf(55_000));
        order.setRewardProcessed(false);

        com.fpt.printhub_3d.entity.OrderItem item = new com.fpt.printhub_3d.entity.OrderItem();
        item.setUnitPrice(BigDecimal.valueOf(40_000));
        item.setQuantity(1);

        com.fpt.printhub_3d.entity.Payment mockPayment3 = new com.fpt.printhub_3d.entity.Payment();
        mockPayment3.setStatus("SUCCESS");
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(mockPayment3));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        when(pointWalletRepository.findById(buyerId)).thenReturn(Optional.empty());
        when(pointWalletRepository.save(any(PointWallet.class))).thenAnswer(inv -> inv.getArgument(0));

        RewardCompletionResponseDTO response = orderService.completeRewards(orderId);

        assertNotNull(response);
        // Only 40k is eligible (4 points), shipping 15k is excluded!
        assertEquals(4, response.rewardPointsEarned());
        assertEquals(4, response.totalRewardPoints());
    }
}
