package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.dashboard.DashboardResponseDTO;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.service.CustomOrderService;
import com.fpt.printhub_3d.service.DashboardService;
import com.fpt.printhub_3d.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final OrderService orderService;
    private final CustomOrderService customOrderService;

    @Override
    public DashboardResponseDTO getDashboard(User user, boolean all) {
        List<OrderResponseDTO> orders = all ? orderService.getAllOrders() : orderService.getMyOrders(user.getId());
        BigDecimal paid = BigDecimal.ZERO;
        long completed = 0;

        for (OrderResponseDTO o : orders) {
            if ("SUCCESS".equals(o.paymentStatus()) || "PAID".equals(o.status())) {
                paid = paid.add(o.totalAmount());
            }
            if ("COMPLETED".equals(o.status())) {
                completed++;
            }
        }

        int customCount = all ? customOrderService.getAllCustomOrders().size() : customOrderService.getMyCustomOrders(user).size();

        return DashboardResponseDTO.builder()
                .orderCount(orders.size())
                .completedCount(completed)
                .paidAmount(paid)
                .customCount(customCount)
                .build();
    }
}
