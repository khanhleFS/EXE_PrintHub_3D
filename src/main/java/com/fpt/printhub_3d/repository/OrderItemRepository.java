package com.fpt.printhub_3d.repository;

import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderIn(Collection<Order> orders);
    List<OrderItem> findByOrderId(UUID orderId);
}
