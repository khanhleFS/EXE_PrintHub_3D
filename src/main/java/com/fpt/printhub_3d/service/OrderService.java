package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.order.OrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import com.fpt.printhub_3d.dto.order.RewardCompletionResponseDTO;
import com.fpt.printhub_3d.entity.User;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    List<OrderResponseDTO> createOrders(OrderCreateRequestDTO request, User buyer);
    RewardCompletionResponseDTO completeRewards(UUID orderId);
    List<OrderResponseDTO> getMyOrders(UUID buyerId);
    OrderResponseDTO getOrderById(UUID id, User currentUser);
    List<OrderResponseDTO> getAllOrders();
    void updateOrderStatus(UUID id, String nextStatus, User currentUser);
}
