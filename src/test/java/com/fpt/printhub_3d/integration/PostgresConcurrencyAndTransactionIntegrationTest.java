package com.fpt.printhub_3d.integration;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.dto.subscription.UserSubscriptionResponseDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.SubscriptionType;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.Enumeration.UserStatus;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.UserSubscriptionService;
import com.fpt.printhub_3d.service.impl.CheckoutRewardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test-local")
class PostgresConcurrencyAndTransactionIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private CheckoutOrderRepository checkoutOrderRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PointWalletRepository pointWalletRepository;
    @Autowired private SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired private UserSubscriptionRepository userSubscriptionRepository;
    @Autowired private CheckoutRewardService checkoutRewardService;
    @Autowired private UserSubscriptionService userSubscriptionService;
    @Autowired private TransactionTemplate transactionTemplate;

    private User createTestUserWithPoints(String prefix, int initialPoints) {
        return transactionTemplate.execute(status -> {
            String unique = UUID.randomUUID().toString().substring(0, 8);
            User user = User.builder()
                    .username(prefix + "_" + unique)
                    .email(prefix + "_" + unique + "@example.com")
                    .password("$2a$10$encodedpassword")
                    .fullName("Test User " + prefix)
                    .phone("09" + (int)(Math.random() * 90000000 + 10000000))
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .isActive(true)
                    .emailVerified(true)
                    .build();
            user = userRepository.save(user);

            PointWallet wallet = PointWallet.builder()
                    .userId(user.getId())
                    .user(user)
                    .balance(initialPoints)
                    .isNew(true)
                    .updatedAt(LocalDateTime.now())
                    .build();
            user.setPointWallet(wallet);
            pointWalletRepository.save(wallet);

            return user;
        });
    }

    private Order createChildOrder(CheckoutOrder co, User buyer, BigDecimal amount) {
        Order o = new Order();
        o.setCheckoutOrder(co);
        o.setBuyer(buyer);
        o.setStatus("COMPLETED");
        o.setTotalAmount(amount);
        o.setCommissionFee(BigDecimal.ZERO);
        o.setRewardProcessed(false);
        o.setRewardPointsEarned(0);
        o.setCreatedAt(Instant.now());
        o.setUpdatedAt(Instant.now());
        return orderRepository.save(o);
    }

    @Test
    @DisplayName("TC-07: Concurrency - Hai đơn cùng user hoàn tất và cộng điểm đồng thời không bị lost update")
    void testConcurrentPointReward_NoLostUpdates() throws InterruptedException {
        User buyer = createTestUserWithPoints("concurrency_reward", 0);

        // Tạo Đơn 1: tiền hàng 100,000 VND -> 10 điểm
        CheckoutOrder co1 = new CheckoutOrder();
        co1.setUser(buyer);
        co1.setShippingMode("COMMON");
        co1.setStatus("COMPLETED");
        co1.setShippingFee(BigDecimal.valueOf(15000));
        co1.setTotalAmount(BigDecimal.valueOf(115000));
        co1 = checkoutOrderRepository.save(co1);

        Payment pay1 = new Payment();
        pay1.setCheckoutOrder(co1);
        pay1.setScope("ALL");
        pay1.setStatus("SUCCESS");
        pay1.setAmount(BigDecimal.valueOf(115000));
        pay1.setGateway("PAYOS");
        pay1.setCreatedAt(Instant.now());
        pay1.setUpdatedAt(Instant.now());
        paymentRepository.save(pay1);

        createChildOrder(co1, buyer, BigDecimal.valueOf(100000));

        // Tạo Đơn 2: tiền hàng 200,000 VND -> 20 điểm
        CheckoutOrder co2 = new CheckoutOrder();
        co2.setUser(buyer);
        co2.setShippingMode("COMMON");
        co2.setStatus("COMPLETED");
        co2.setShippingFee(BigDecimal.valueOf(15000));
        co2.setTotalAmount(BigDecimal.valueOf(215000));
        co2 = checkoutOrderRepository.save(co2);

        Payment pay2 = new Payment();
        pay2.setCheckoutOrder(co2);
        pay2.setScope("ALL");
        pay2.setStatus("SUCCESS");
        pay2.setAmount(BigDecimal.valueOf(215000));
        pay2.setGateway("PAYOS");
        pay2.setCreatedAt(Instant.now());
        pay2.setUpdatedAt(Instant.now());
        paymentRepository.save(pay2);

        createChildOrder(co2, buyer, BigDecimal.valueOf(200000));

        // 2 thread cùng gọi checkoutRewardService.rewardCheckoutOrderCumulative đồng thời
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger totalAwarded = new AtomicInteger(0);

        UUID co1Id = co1.getId();
        UUID co2Id = co2.getId();

        executor.submit(() -> {
            try {
                startLatch.await();
                int pts = checkoutRewardService.rewardCheckoutOrderCumulative(co1Id, buyer);
                totalAwarded.addAndGet(pts);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                int pts = checkoutRewardService.rewardCheckoutOrderCumulative(co2Id, buyer);
                totalAwarded.addAndGet(pts);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Hai thread phải hoàn thành trong 10 giây");
        assertEquals(30, totalAwarded.get(), "Tổng điểm được cấp phải là 10 + 20 = 30");

        PointWallet updatedWallet = pointWalletRepository.findById(buyer.getId()).orElseThrow();
        assertEquals(30, updatedWallet.getBalance(), "Số dư ví trong PostgreSQL phải chính xác 30, không được mất cập nhật");
    }

    @Test
    @DisplayName("TC-08: Concurrency - Cộng điểm đồng thời với đổi gói không bị mất cập nhật số dư")
    void testConcurrentPointRewardAndRedeem() throws InterruptedException {
        User buyer = createTestUserWithPoints("concurrency_redeem", 100);

        // Gói cần 100 điểm
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("VIP Test Concurrency")
                .type(SubscriptionType.CUSTOMER_VIP)
                .price(BigDecimal.valueOf(50000))
                .benefits("Free shipping")
                .freeShipping(true)
                .durationDays(30)
                .requiredPoints(100)
                .isActive(true)
                .build();
        plan = subscriptionPlanRepository.save(plan);

        // Đơn hàng tặng thêm 50 điểm (tiền hàng 500,000 VND)
        CheckoutOrder co = new CheckoutOrder();
        co.setUser(buyer);
        co.setShippingMode("COMMON");
        co.setStatus("COMPLETED");
        co.setShippingFee(BigDecimal.ZERO);
        co.setTotalAmount(BigDecimal.valueOf(500000));
        co = checkoutOrderRepository.save(co);

        Payment pay = new Payment();
        pay.setCheckoutOrder(co);
        pay.setScope("ALL");
        pay.setStatus("SUCCESS");
        pay.setAmount(BigDecimal.valueOf(500000));
        pay.setGateway("PAYOS");
        pay.setCreatedAt(Instant.now());
        pay.setUpdatedAt(Instant.now());
        paymentRepository.save(pay);

        createChildOrder(co, buyer, BigDecimal.valueOf(500000));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        UUID coId = co.getId();
        UUID planId = plan.getId();

        executor.submit(() -> {
            try {
                startLatch.await();
                checkoutRewardService.rewardCheckoutOrderCumulative(coId, buyer);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                userSubscriptionService.redeemSubscription(buyer.getId(), planId);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Hai thao tác phải hoàn thành trong 10 giây");

        PointWallet finalWallet = pointWalletRepository.findById(buyer.getId()).orElseThrow();
        // Ban đầu: 100, Cộng: +50, Đổi gói: -100 => Cuối cùng phải là 50 điểm
        assertEquals(50, finalWallet.getBalance(), "Số dư ví cuối cùng phải chính xác là 50 điểm");
    }

    @Test
    @DisplayName("TC-10: Subscription - Gia hạn trước cùng gói sẽ nối tiếp kỳ cuối đã mua")
    void testSubscriptionSequentialRenewal() {
        User buyer = createTestUserWithPoints("renewal_seq", 500);

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("VIP Gói Nối Tiếp")
                .type(SubscriptionType.CUSTOMER_VIP)
                .price(BigDecimal.valueOf(60000))
                .benefits("Freeship")
                .freeShipping(true)
                .durationDays(30)
                .requiredPoints(100)
                .isActive(true)
                .build();
        plan = subscriptionPlanRepository.save(plan);

        // Kỳ 1: Đổi lần đầu
        UserSubscriptionResponseDTO sub1 = userSubscriptionService.redeemSubscription(buyer.getId(), plan.getId());
        assertNotNull(sub1);
        LocalDateTime firstStart = sub1.startDate();
        LocalDateTime firstEnd = sub1.endDate();
        assertTrue(firstEnd.isAfter(firstStart));

        // Kỳ 2: Gia hạn lần hai khi kỳ 1 vẫn còn hiệu lực
        UserSubscriptionResponseDTO sub2 = userSubscriptionService.redeemSubscription(buyer.getId(), plan.getId());
        assertNotNull(sub2);
        LocalDateTime secondStart = sub2.startDate();
        LocalDateTime secondEnd = sub2.endDate();

        // Kiểm tra nối tiếp chính xác: secondStart == firstEnd, secondEnd == firstEnd + 30 days
        assertEquals(firstEnd.getDayOfYear(), secondStart.getDayOfYear(), "Kỳ 2 phải bắt đầu nối tiếp từ ngày kết thúc của kỳ 1");
        assertEquals(secondStart.plusDays(30).getDayOfYear(), secondEnd.getDayOfYear(), "Kỳ 2 phải kéo dài 30 ngày");

        List<UserSubscription> allSubs = userSubscriptionRepository.findAllActiveAndFutureSubscriptionsByUser(buyer);
        assertEquals(2, allSubs.size(), "Cả 2 kỳ đều phải tồn tại và có hiệu lực");
    }

    @Test
    @DisplayName("TC-10 / SUB-03: Subscription - Đổi sang gói khác khi kỳ cũ còn hạn bị chặn (ApiException)")
    void testSubscriptionSwitchPlan_BlockedWhenActive() {
        User buyer = createTestUserWithPoints("switch_blocked", 500);

        SubscriptionPlan planA = SubscriptionPlan.builder()
                .name("Gói A Silver")
                .type(SubscriptionType.CUSTOMER)
                .price(BigDecimal.valueOf(30000))
                .benefits("Giảm ship 10k")
                .freeShipping(false)
                .shippingDiscountAmount(BigDecimal.valueOf(10000))
                .durationDays(30)
                .requiredPoints(100)
                .isActive(true)
                .build();
        planA = subscriptionPlanRepository.save(planA);

        SubscriptionPlan planB = SubscriptionPlan.builder()
                .name("Gói B Gold")
                .type(SubscriptionType.CUSTOMER_VIP)
                .price(BigDecimal.valueOf(80000))
                .benefits("Freeship")
                .freeShipping(true)
                .durationDays(30)
                .requiredPoints(200)
                .isActive(true)
                .build();
        planB = subscriptionPlanRepository.save(planB);

        // Đổi Gói A
        userSubscriptionService.redeemSubscription(buyer.getId(), planA.getId());

        PointWallet walletAfterA = pointWalletRepository.findById(buyer.getId()).orElseThrow();
        assertEquals(400, walletAfterA.getBalance());

        // Thử đổi sang Gói B khi Gói A còn hạn -> Bị chặn
        UUID planBId = planB.getId();
        ApiException ex = assertThrows(ApiException.class, () ->
                userSubscriptionService.redeemSubscription(buyer.getId(), planBId)
        );

        assertTrue(ex.getMessage().contains("Gói A Silver"), "Phải giải thích rõ gói A còn hạn");

        // Số dư ví không đổi, kỳ Gói A vẫn nguyên vẹn
        PointWallet walletAfterFailedB = pointWalletRepository.findById(buyer.getId()).orElseThrow();
        assertEquals(400, walletAfterFailedB.getBalance(), "Điểm thưởng không bị trừ");

        List<UserSubscription> activeSubs = userSubscriptionRepository.findActiveSubscriptionsByUser(buyer);
        assertEquals(1, activeSubs.size());
        assertTrue(activeSubs.get(0).getIsActive(), "Kỳ gói A vẫn hoạt động bình thường");
    }

    @Test
    @DisplayName("TC-11: Subscription - Admin sửa quyền lợi gói sau khi user kích hoạt không làm thay đổi snapshot")
    void testSubscriptionSnapshot_ImmutableAfterPlanUpdate() {
        User buyer = createTestUserWithPoints("snapshot_immutability", 500);

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("VIP Ban Đầu")
                .type(SubscriptionType.CUSTOMER_VIP)
                .price(BigDecimal.valueOf(50000))
                .benefits("Freeship 100%")
                .freeShipping(true)
                .shippingDiscountAmount(BigDecimal.valueOf(20000))
                .durationDays(30)
                .requiredPoints(100)
                .isActive(true)
                .build();
        plan = subscriptionPlanRepository.save(plan);

        // User đổi gói
        UserSubscriptionResponseDTO userSub = userSubscriptionService.redeemSubscription(buyer.getId(), plan.getId());
        assertNotNull(userSub);

        // Admin sửa gói sau đó: tăng giá, bỏ freeship, giảm mức giảm ship
        plan.setPrice(BigDecimal.valueOf(150000));
        plan.setFreeShipping(false);
        plan.setShippingDiscountAmount(BigDecimal.valueOf(5000));
        subscriptionPlanRepository.save(plan);

        // Đọc lại UserSubscription từ PostgreSQL: snapshot không bị đổi
        UserSubscription persistedSub = userSubscriptionRepository.findById(userSub.subscriptionId()).orElseThrow();
        assertEquals(30, persistedSub.getDurationDays(), "Snapshot durationDays không bị đổi");
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(persistedSub.getAppliedPrice()), "Snapshot price không bị đổi");
        assertTrue(persistedSub.getAppliedFreeShipping(), "Snapshot freeShipping vẫn là true");
        assertEquals(0, BigDecimal.valueOf(20000).compareTo(persistedSub.getAppliedShippingDiscountAmount()), "Snapshot shipping discount không bị đổi");
    }

    @Test
    @DisplayName("TC-13 / SQL Unique Constraint: uq_checkout_orders_user_idempotency ngăn trùng lặp đơn checkout")
    void testIdempotencyUniqueConstraint_Postgres() {
        User buyer = createTestUserWithPoints("idempotency_constraint", 0);
        String testKey = "idemp_" + UUID.randomUUID();

        CheckoutOrder co1 = new CheckoutOrder();
        co1.setUser(buyer);
        co1.setShippingMode("COMMON");
        co1.setStatus("PENDING_QUOTE");
        co1.setIdempotencyKey(testKey);

        checkoutOrderRepository.save(co1);

        // Cố gắng insert đơn thứ 2 cùng user và cùng idempotency_key
        CheckoutOrder co2 = new CheckoutOrder();
        co2.setUser(buyer);
        co2.setShippingMode("COMMON");
        co2.setStatus("PENDING_QUOTE");
        co2.setIdempotencyKey(testKey);

        assertThrows(DataIntegrityViolationException.class, () -> {
            transactionTemplate.execute(status -> checkoutOrderRepository.saveAndFlush(co2));
        }, "PostgreSQL phải quăng DataIntegrityViolationException do partial unique index uq_checkout_orders_user_idempotency");
    }
}
