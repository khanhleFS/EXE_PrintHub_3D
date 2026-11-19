package com.fpt.printhub_3d;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.printhub_3d.common.security.CustomUserDetail;
import com.fpt.printhub_3d.entity.CheckoutOrder;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.EmailOutbox;
import com.fpt.printhub_3d.entity.Enumeration.EmailOutboxStatus;
import com.fpt.printhub_3d.entity.Enumeration.SubscriptionType;
import com.fpt.printhub_3d.entity.Enumeration.UserRole;
import com.fpt.printhub_3d.entity.Enumeration.UserStatus;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.entity.PointWallet;
import com.fpt.printhub_3d.entity.SubscriptionPlan;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CheckoutOrderRepository;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.repository.EmailOutboxRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.repository.PointTransactionRepository;
import com.fpt.printhub_3d.repository.PointWalletRepository;
import com.fpt.printhub_3d.repository.SubscriptionPlanRepository;
import com.fpt.printhub_3d.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:e2e01;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "app.email-outbox.enabled=false",
        "app.email-outbox.enqueue-enabled=true"
})
@AutoConfigureMockMvc
class E2e01AcceptanceTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private SubscriptionPlanRepository plans;
    @Autowired private CheckoutOrderRepository checkouts;
    @Autowired private CustomOrderRepository customs;
    @Autowired private PaymentRepository payments;
    @Autowired private PointWalletRepository wallets;
    @Autowired private PointTransactionRepository pointTransactions;
    @Autowired private EmailOutboxRepository outbox;
    @Autowired private TransactionTemplate transactions;
    @MockitoBean private PayOS payOS;

    @Test
    void e2e01ReconcilesCheckoutMembershipRewardAndEmailOutbox() throws Exception {
        User admin = account("admin", "0900000000", UserRole.ADMIN);
        User u1 = account("u1", "0900000001", UserRole.USER);
        User u2 = account("u2", "0900000002", UserRole.USER);
        User u3 = account("u3", "0900000003", UserRole.USER);
        transactions.executeWithoutResult(ignored -> wallets.saveAndFlush(
                PointWallet.builder().user(users.getReferenceById(u2.getId())).balance(100).build()));
        var plan = plans.saveAndFlush(SubscriptionPlan.builder().type(SubscriptionType.CUSTOMER)
                .name("G1").price(new BigDecimal("50000")).requiredPoints(80).benefits("E2E-01")
                .structuredBenefits("""
                        {"discount":{"percent":10,"appliesTo":["CUSTOM"]},
                         "shipping":{"freeUsesPerTerm":1,"maxDiscountAmountPerUse":30000,"appliesTo":["CUSTOM"]}}
                        """).build());

        PaymentRequestsService gateway = mock(PaymentRequestsService.class);
        when(payOS.paymentRequests()).thenReturn(gateway);
        when(gateway.create(any(CreatePaymentLinkRequest.class))).thenAnswer(call -> {
            CreatePaymentLinkRequest request = call.getArgument(0);
            var link = mock(CreatePaymentLinkResponse.class);
            when(link.getCheckoutUrl()).thenReturn("https://example.invalid/pay/" + request.getOrderCode());
            return link;
        });
        UUID s1 = purchase(u1, plan.getId(), "e2e01-s1");
        Payment s1Receipt = gatewayPayment(u1, gateway, "SUBSCRIPTION_PURCHASE", s1, 50000L, PaymentLinkStatus.PAID);
        UUID s3 = purchase(u3, plan.getId(), "e2e01-s3");
        gatewayPayment(u3, gateway, "SUBSCRIPTION_PURCHASE", s3, 0L, PaymentLinkStatus.CANCELLED);
        for (int i = 0; i < 2; i++) {
            api(post("/api/subscriptions/redeem/{planId}", plan.getId()), u2, Map.of("idempotencyKey", "e2e01-s2"));
        }
        assertEquals(20, wallets.findByUserId(u2.getId()).orElseThrow().getBalance());

        CheckoutOrder checkout = new CheckoutOrder();
        checkout.setUser(u1);
        checkout.setOrderType("CUSTOM");
        checkout.setPaymentMethod("PAYOS");
        checkout.setShippingFee(new BigDecimal("30000"));
        UUID c1 = checkouts.saveAndFlush(checkout).getId();
        CustomOrder custom = new CustomOrder();
        custom.setCheckoutOrder(checkout);
        custom.setBuyer(u1);
        custom.setStatus("REQUESTED");
        custom.setRulerModel("Thiết kế C1");
        custom.setCreatedAt(checkout.getCreatedAt());
        custom.setUpdatedAt(checkout.getUpdatedAt());
        UUID design = customs.saveAndFlush(custom).getId();

        String checkoutAdmin = "/api/admin/checkout-orders/" + c1;
        var quotation = Map.of("items", List.of(Map.of("customOrderId", design, "unitPrice", 100000)));
        JsonNode quote = api(put(checkoutAdmin + "/quote"), admin, quotation);
        long version = quote.path("summaryVersion").asLong();
        assertEquals(version, api(put(checkoutAdmin + "/quote"), admin, quotation).path("summaryVersion").asLong());
        JsonNode price = quote.path("pricingBreakdown");
        assertEquals("ESTIMATED", price.path("pricingStage").asText());
        assertMoney(10000, price, "itemDiscountAmount");
        assertMoney(30000, price, "shippingDiscountAmount");
        assertMoney(90000, price, "totalPayable");

        JsonNode confirmation = api(post("/api/checkout-orders/{id}/confirm", c1), u1,
                Map.of("summaryVersion", version, "expectedTotalPayable", 90000, "paymentMethod", "PAYOS"));
        assertEquals("LOCKED", confirmation.at("/pricingBreakdown/pricingStage").asText());
        assertMoney(90000, confirmation.path("pricingBreakdown"), "totalPayable");
        Payment c1Receipt = gatewayPayment(u1, gateway, "CHECKOUT_ORDER", c1, 90000L, PaymentLinkStatus.PAID);
        verify(gateway).create(argThat(request -> request.getAmount() == 90000L));

        assertReports(admin, 0);
        assertEquals(0, walletBalance(u1.getId()));
        for (String orderStatus : List.of("PREPARING", "PRINTING", "SHIPPING", "COMPLETED", "COMPLETED")) {
            assertEquals(orderStatus, api(put(checkoutAdmin + "/status"), admin, Map.of("status", orderStatus))
                    .path("status").asText());
        }
        assertReports(admin, 90000);
        assertEquals(9, walletBalance(u1.getId()));
        var replay = api(post("/api/custom-orders/{id}/complete-rewards", design), admin, null);
        assertTrue(replay.path("alreadyProcessed").asBoolean());
        assertEquals(9, walletBalance(u1.getId()));
        var credits = pointTransactions.findBySourceTypeAndSourceId("CHECKOUT_ORDER", c1.toString());
        assertEquals(1, credits.size());
        assertEquals(9, credits.getFirst().getAmount());
        assertNotNull(checkouts.findById(c1).orElseThrow().getCompletedAt());

        String history = "/api/admin/subscriptions/transactions";
        var historyIds = new HashSet<String>();
        for (int page = 0; page < 3; page++) {
            JsonNode result = api(get(history).param("page", Integer.toString(page)).param("size", "1"), admin, null);
            assertEquals(3, result.path("totalElements").asInt());
            assertEquals(1, result.path("items").size());
            assertTrue(historyIds.add(result.at("/items/0/id").asText()));
            assertMoney(50000, result.path("summary"), "subscriptionCashVnd");
        }
        var redeemed = api(get(history).param("method", "POINTS_REDEEM"), admin, null);
        assertEquals(1, redeemed.path("totalElements").asInt());
        assertEquals(80, redeemed.at("/items/0/pointsSpent").asInt());
        assertEquals("CANCELLED", api(get(history).param("userId", u3.getId().toString()), admin, null)
                .at("/items/0/status").asText());
        assertMoney(0, redeemed.path("summary"), "subscriptionCashVnd");
        mvc.perform(get(history).with(user(new CustomUserDetail(u1)))).andExpect(status().isForbidden());

        List<EmailOutbox> events = outbox.findAll();
        assertEquals(3, events.size());
        assertEquals(Set.of("QUOTE:CHECKOUT_ORDER:" + c1 + ":v" + version,
                        "RECEIPT:PAYMENT:" + c1Receipt.getId(), "RECEIPT:PAYMENT:" + s1Receipt.getId()),
                events.stream().map(EmailOutbox::getEventKey).collect(Collectors.toSet()));
        assertTrue(events.stream().allMatch(event -> event.getStatus() == EmailOutboxStatus.PENDING));
        assertTrue(events.stream().allMatch(event -> u1.getEmail().equals(event.getRecipientEmail())));
        for (EmailOutbox event : events) {
            String amount = event.getSourceId().equals(s1.toString()) ? "50.000" : "90.000";
            assertTrue(event.getHtmlBody().contains(amount), event.getEventKey());
        }
        System.out.printf("E2E-01 C1=%s C1Payment=%s S1=%s S1Payment=%s S2Tx=%s S3=%s rewardTx=%s outbox=%s%n",
                c1, c1Receipt.getId(), s1, s1Receipt.getId(), redeemed.at("/items/0/id").asText(),
                s3, credits.getFirst().getId(),
                events.stream().map(EmailOutbox::getId).toList());
    }

    private User account(String name, String phone, UserRole role) {
        return users.saveAndFlush(User.builder().username(name).email(name + "@example.invalid").phone(phone)
                .fullName(name).password("unused-test-password").status(UserStatus.ACTIVE).emailVerified(true)
                .role(role).build());
    }

    private UUID purchase(User buyer, UUID plan, String key) throws Exception {
        return UUID.fromString(api(post("/api/subscriptions/purchases"), buyer, Map.of("planId", plan,
                "idempotencyKey", key)).path("purchaseId").asText());
    }

    private Payment gatewayPayment(User buyer, PaymentRequestsService gateway, String type, UUID source,
                                   long amount, PaymentLinkStatus disposition) throws Exception {
        var link = api(post("/api/payments/create-link"), buyer, Map.of("orderId", source, "orderType", type));
        long code = link.path("orderCode").asLong();
        assertTrue(code > 0, link.toString());
        var live = mock(PaymentLink.class);
        when(live.getOrderCode()).thenReturn(code);
        when(live.getAmountPaid()).thenReturn(amount);
        when(live.getStatus()).thenReturn(disposition);
        when(gateway.get(code)).thenReturn(live);
        for (int i = 0; i < 2; i++) {
            assertEquals(disposition.name(), api(get("/api/payments/verify/{orderCode}", code), buyer, null)
                    .path("status").asText());
        }
        return payments.findByTransactionId(Long.toString(code)).orElseThrow();
    }

    private int walletBalance(UUID owner) {
        return wallets.findByUserId(owner).map(PointWallet::getBalance).orElse(0);
    }

    private void assertReports(User admin, int completed) throws Exception {
        String from = Instant.now().minusSeconds(3600).toString();
        for (String route : List.of("/api/admin/dashboard", "/api/admin/finance/summary")) {
            var summary = api(get(route).param("from", from), admin, null).path("financialSummary");
            assertMoney(140000, summary, "totalCashVnd");
            assertMoney(90000, summary, "orderCashVnd");
            assertMoney(50000, summary, "subscriptionCashVnd");
            assertMoney(0, summary, "shippingCashVnd");
            assertMoney(completed, summary, "completedGoodsValueVnd");
        }
    }

    private JsonNode api(MockHttpServletRequestBuilder request, User actor, Object body) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        }
        var response = mvc.perform(request.with(user(new CustomUserDetail(actor))))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray()).path("result");
    }

    private void assertMoney(int expected, JsonNode node, String field) {
        assertTrue(node.path(field).isNumber(), node.toString());
        assertEquals(0, node.path(field).decimalValue().compareTo(BigDecimal.valueOf(expected)), field);
    }
}
