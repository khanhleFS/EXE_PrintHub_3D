package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.OrderErrorCode;
import com.fpt.printhub_3d.dto.order.RewardCompletionResponseDTO;
import com.fpt.printhub_3d.dto.point.PointTransactionResponseDTO;
import com.fpt.printhub_3d.dto.point.PointWalletResponseDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.PointTransactionType;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RewardPointsLifecycleTest {

    @Mock private ProductRepository productRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ShippingInfoRepository shippingInfoRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PointWalletRepository pointWalletRepository;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private NotificationService notificationService;
    @Mock private ReviewRepository reviewRepository;
    @Mock private InventoryService inventoryService;
    @Mock private OrderTransitionPolicy orderTransitionPolicy;
    @Mock private CustomOrderRepository customOrderRepository;
    @Mock private FileAssetRepository fileAssetRepository;
    @Mock private CloudinaryService cloudinaryService;

    private OrderServiceImpl orderService;
    private CustomOrderServiceImpl customOrderService;
    private PointWalletServiceImpl pointWalletService;

    private User testBuyer;
    private PointWallet testWallet;
    private UUID testOrderId;
    private UUID testCustomOrderId;

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
                inventoryService,
                orderTransitionPolicy
        );

        customOrderService = new CustomOrderServiceImpl(
                customOrderRepository,
                fileAssetRepository,
                paymentRepository,
                userRepository,
                notificationService,
                cloudinaryService,
                orderTransitionPolicy,
                pointWalletRepository,
                pointTransactionRepository
        );

        pointWalletService = new PointWalletServiceImpl(
                pointWalletRepository,
                pointTransactionRepository
        );

        testBuyer = new User();
        testBuyer.setId(UUID.randomUUID());
        testBuyer.setUsername("buyer1");
        testBuyer.setFullName("Nguyen Van Buyer");
        testBuyer.setRole(UserRole.USER);

        testWallet = PointWallet.builder()
                .userId(testBuyer.getId())
                .user(testBuyer)
                .balance(50)
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
        testBuyer.setPointWallet(testWallet);

        testOrderId = UUID.randomUUID();
        testCustomOrderId = UUID.randomUUID();
    }

    @Test
    @DisplayName("1. Đơn Marketplace COMPLETED + Đã thanh toán: Cộng điểm đúng tỷ lệ 10.000đ = 1 điểm & Ghi PointTransaction EARN")
    void testMarketplaceOrderCompleteRewardsSuccess() {
        Order order = new Order();
        order.setId(testOrderId);
        order.setBuyer(testBuyer);
        order.setStatus("COMPLETED");
        order.setTotalAmount(new BigDecimal("150000")); // 150.000đ -> 15 điểm
        order.setRewardProcessed(false);

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        payment.setStatus("SUCCESS");

        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(testOrderId)).thenReturn(Optional.of(payment));
        when(pointWalletRepository.findByUserIdForUpdate(testBuyer.getId())).thenReturn(Optional.of(testWallet));
        when(pointWalletRepository.save(any(PointWallet.class))).thenAnswer(i -> i.getArgument(0));

        RewardCompletionResponseDTO response = orderService.completeRewards(testOrderId);

        assertNotNull(response);
        assertEquals(15, response.rewardPointsEarned());
        assertEquals(65, response.totalRewardPoints()); // 50 + 15
        assertFalse(response.alreadyProcessed());

        assertTrue(order.getRewardProcessed());
        assertEquals(15, order.getRewardPointsEarned());
        assertEquals(65, testWallet.getBalance());

        // Kiểm tra audit ledger PointTransaction
        ArgumentCaptor<PointTransaction> txCaptor = ArgumentCaptor.forClass(PointTransaction.class);
        verify(pointTransactionRepository, times(1)).save(txCaptor.capture());
        PointTransaction savedTx = txCaptor.getValue();
        assertEquals(15, savedTx.getAmount());
        assertEquals(PointTransactionType.EARN, savedTx.getType());
        assertTrue(savedTx.getDescription().contains(testOrderId.toString()));
    }

    @Test
    @DisplayName("2. Đơn Marketplace < 10.000đ (Dưới ngưỡng): Điểm thưởng = 0, đánh dấu đã xử lý và không ghi ledger ảo")
    void testMarketplaceOrderBelowThresholdEarnsZero() {
        Order order = new Order();
        order.setId(testOrderId);
        order.setBuyer(testBuyer);
        order.setStatus("COMPLETED");
        order.setTotalAmount(new BigDecimal("9999")); // < 10.000đ -> 0 điểm
        order.setRewardProcessed(false);

        Payment payment = new Payment();
        payment.setStatus("SUCCESS");

        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(testOrderId)).thenReturn(Optional.of(payment));
        when(pointWalletRepository.findByUserIdForUpdate(testBuyer.getId())).thenReturn(Optional.of(testWallet));
        when(pointWalletRepository.save(any(PointWallet.class))).thenAnswer(i -> i.getArgument(0));

        RewardCompletionResponseDTO response = orderService.completeRewards(testOrderId);

        assertEquals(0, response.rewardPointsEarned());
        assertEquals(50, response.totalRewardPoints());
        assertTrue(order.getRewardProcessed());
        assertEquals(0, order.getRewardPointsEarned());

        // Không ghi ledger cho 0 điểm
        verify(pointTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. Tính Idempotent: Gọi completeRewards lần 2 cho đơn đã xử lý không được cộng điểm trùng")
    void testMarketplaceOrderCompleteRewardsIdempotent() {
        Order order = new Order();
        order.setId(testOrderId);
        order.setBuyer(testBuyer);
        order.setStatus("COMPLETED");
        order.setTotalAmount(new BigDecimal("200000"));
        order.setRewardProcessed(true);
        order.setRewardPointsEarned(20);

        Payment payment = new Payment();
        payment.setStatus("SUCCESS");

        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(testOrderId)).thenReturn(Optional.of(payment));

        RewardCompletionResponseDTO response = orderService.completeRewards(testOrderId);

        assertTrue(response.alreadyProcessed());
        assertEquals(20, response.rewardPointsEarned());
        assertEquals(50, testWallet.getBalance()); // Số dư ví không đổi

        verify(pointWalletRepository, never()).save(any());
        verify(pointTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. Chặn nhận điểm khi đơn COMPLETED nhưng thanh toán chưa SUCCESS (Chưa trả tiền)")
    void testMarketplaceOrderNotPaidThrowsException() {
        Order order = new Order();
        order.setId(testOrderId);
        order.setBuyer(testBuyer);
        order.setStatus("COMPLETED");
        order.setTotalAmount(new BigDecimal("100000"));
        order.setRewardProcessed(false);

        Payment payment = new Payment();
        payment.setStatus("PENDING"); // Chưa thanh toán

        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(testOrderId)).thenReturn(Optional.of(payment));

        ApiException ex = assertThrows(ApiException.class, () -> orderService.completeRewards(testOrderId));
        assertEquals(OrderErrorCode.PAYMENT_REQUIRED, ex.getErrorCode());
        assertFalse(order.getRewardProcessed());
    }

    @Test
    @DisplayName("5. Đơn in 3D Custom COMPLETED + Đã thanh toán: Tự động cộng điểm vào ví và ghi PointTransaction")
    void testCustomOrderCompleteRewardsSuccess() {
        CustomOrder customOrder = new CustomOrder();
        customOrder.setId(testCustomOrderId);
        customOrder.setBuyer(testBuyer);
        customOrder.setStatus("COMPLETED");
        customOrder.setQuotedPrice(new BigDecimal("300000"));
        customOrder.setQuantity(1);
        customOrder.setRewardProcessed(false);

        Payment payment = new Payment();
        payment.setStatus("SUCCESS");

        when(customOrderRepository.findLockedById(testCustomOrderId)).thenReturn(Optional.of(customOrder));
        when(paymentRepository.findByCustomOrderId(testCustomOrderId)).thenReturn(Optional.of(payment));
        when(pointWalletRepository.findByUserIdForUpdate(testBuyer.getId())).thenReturn(Optional.of(testWallet));
        when(pointWalletRepository.save(any(PointWallet.class))).thenAnswer(i -> i.getArgument(0));

        RewardCompletionResponseDTO response = customOrderService.completeRewards(testCustomOrderId);

        assertNotNull(response);
        assertEquals(30, response.rewardPointsEarned()); // 300.000 / 10.000 = 30 điểm
        assertEquals(80, response.totalRewardPoints()); // 50 + 30
        assertFalse(response.alreadyProcessed());

        assertTrue(customOrder.getRewardProcessed());
        assertEquals(30, customOrder.getRewardPointsEarned());
        assertEquals(80, testWallet.getBalance());

        // Kiểm tra audit ledger
        ArgumentCaptor<PointTransaction> txCaptor = ArgumentCaptor.forClass(PointTransaction.class);
        verify(pointTransactionRepository, times(1)).save(txCaptor.capture());
        assertEquals(30, txCaptor.getValue().getAmount());
        assertEquals(PointTransactionType.EARN, txCaptor.getValue().getType());
        assertTrue(txCaptor.getValue().getDescription().contains(testCustomOrderId.toString()));
    }

    @Test
    @DisplayName("6. Đơn in 3D Custom gọi completeRewards lần 2: Đảm bảo tính Idempotent")
    void testCustomOrderCompleteRewardsIdempotent() {
        CustomOrder customOrder = new CustomOrder();
        customOrder.setId(testCustomOrderId);
        customOrder.setBuyer(testBuyer);
        customOrder.setStatus("COMPLETED");
        customOrder.setQuotedPrice(new BigDecimal("300000"));
        customOrder.setRewardProcessed(true);
        customOrder.setRewardPointsEarned(30);

        Payment payment = new Payment();
        payment.setStatus("SUCCESS");

        when(customOrderRepository.findLockedById(testCustomOrderId)).thenReturn(Optional.of(customOrder));
        when(paymentRepository.findByCustomOrderId(testCustomOrderId)).thenReturn(Optional.of(payment));

        RewardCompletionResponseDTO response = customOrderService.completeRewards(testCustomOrderId);

        assertTrue(response.alreadyProcessed());
        assertEquals(30, response.rewardPointsEarned());
        assertEquals(50, testWallet.getBalance()); // Không cộng dồn lần 2

        verify(pointWalletRepository, never()).save(any());
        verify(pointTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("7. PointWalletService: Đọc thông tin số dư ví và lịch sử biến động điểm chính xác")
    void testPointWalletServiceQueryBalanceAndTransactions() {
        when(pointWalletRepository.findByUserId(testBuyer.getId())).thenReturn(Optional.of(testWallet));

        PointTransaction tx1 = PointTransaction.builder()
                .id(1L)
                .pointWallet(testWallet)
                .amount(15)
                .type(PointTransactionType.EARN)
                .description("Thưởng hoàn thành đơn #1")
                .createdAt(LocalDateTime.now())
                .build();

        Page<PointTransaction> page = new PageImpl<>(List.of(tx1), PageRequest.of(0, 10), 1);
        when(pointTransactionRepository.findByPointWallet_UserIdOrderByCreatedAtDesc(eq(testBuyer.getId()), any()))
                .thenReturn(page);

        PointWalletResponseDTO walletDTO = pointWalletService.getMyWallet(testBuyer.getId());
        assertEquals(50, walletDTO.balance());
        assertEquals(50, walletDTO.availableToSpend());

        Page<PointTransactionResponseDTO> txPage = pointWalletService.getMyTransactions(testBuyer.getId(), PageRequest.of(0, 10));
        assertEquals(1, txPage.getTotalElements());
        assertEquals(15, txPage.getContent().get(0).amount());
        assertEquals(PointTransactionType.EARN, txPage.getContent().get(0).type());
    }
}

