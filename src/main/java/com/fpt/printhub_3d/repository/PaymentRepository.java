package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByCustomOrderId(UUID customOrderId);

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByCheckoutOrderId(UUID checkoutOrderId);

    Optional<Payment> findBySubscriptionPurchaseId(UUID subscriptionPurchaseId);

    @EntityGraph(attributePaths = "subscriptionPurchase")
    List<Payment> findBySubscriptionPurchaseIsNotNull();

    Optional<Payment> findByTransactionId(String transactionId);

    boolean existsByTransactionId(String transactionId);

    @EntityGraph(attributePaths = {"checkoutOrder", "order", "order.checkoutOrder", "customOrder",
            "customOrder.checkoutOrder", "subscriptionPurchase"})
    @Query("""
            SELECT p FROM Payment p WHERE p.status IN ('SUCCESS', 'PENDING_RECONCILIATION')
                 OR p.reconciliationAmount IS NOT NULL
            """)
    List<Payment> findReportablePayments();

    interface PaymentTarget {
        UUID getOrderId();
        UUID getCustomOrderId();
        UUID getCheckoutOrderId();
        UUID getParentCheckoutOrderId();
        UUID getSubscriptionPurchaseId();
    }

    @Query("""
            SELECT o.id AS orderId, c.id AS customOrderId, co.id AS checkoutOrderId,
                   sp.id AS subscriptionPurchaseId, COALESCE(op.id, cp.id) AS parentCheckoutOrderId
            FROM Payment p LEFT JOIN p.order o LEFT JOIN p.customOrder c
                 LEFT JOIN p.checkoutOrder co LEFT JOIN p.subscriptionPurchase sp
                 LEFT JOIN o.checkoutOrder op LEFT JOIN c.checkoutOrder cp
            WHERE p.transactionId = :code
            """)
    Optional<PaymentTarget> findTargetByTransactionId(@Param("code") String code);

    @Query("""
            SELECT p.transactionId FROM Payment p
            WHERE p.status = 'PENDING' AND p.gateway = 'PAYOS' AND p.expiresAt <= :now
                  AND p.transactionId IS NOT NULL
                  AND (p.checkoutOrder IS NOT NULL OR p.customOrder IS NOT NULL OR p.subscriptionPurchase IS NOT NULL)
            """)
    List<String> findExpiredQuotePaymentCodes(@Param("now") Instant now);
}
