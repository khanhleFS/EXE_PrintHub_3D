package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Order;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Query(value = "select coalesce(sum(commission_fee), cast(0 as decimal(18,2))) from orders", nativeQuery = true)
    BigDecimal sumCommissionFee();

    @Query(value = "select coalesce(sum(commission_fee), cast(0 as decimal(18,2))) from orders where (:from is null or created_at >= :from) and (:to is null or created_at <= :to)", nativeQuery = true)
    BigDecimal sumCommissionFeeBetween(Instant from, Instant to);

    @Query(value = "select count(*) from orders", nativeQuery = true)
    Long countOrders();
}
