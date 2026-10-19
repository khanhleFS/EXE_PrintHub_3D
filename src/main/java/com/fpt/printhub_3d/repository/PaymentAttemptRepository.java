package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findByProviderCode(Long providerCode);

    Optional<PaymentAttempt> findFirstByCheckoutOrderIdAndScopeOrderByCreatedAtDesc(UUID checkoutOrderId, String scope);

    Optional<PaymentAttempt> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);

    Optional<PaymentAttempt> findFirstByCustomOrderIdOrderByCreatedAtDesc(UUID customOrderId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT a FROM PaymentAttempt a WHERE a.id = :id")
    Optional<PaymentAttempt> findLockedById(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT a FROM PaymentAttempt a WHERE a.providerCode = :providerCode")
    Optional<PaymentAttempt> findLockedByProviderCode(@org.springframework.data.repository.query.Param("providerCode") Long providerCode);

    List<PaymentAttempt> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    List<PaymentAttempt> findByCheckoutOrderIdOrderByCreatedAtDesc(UUID checkoutOrderId);
}
