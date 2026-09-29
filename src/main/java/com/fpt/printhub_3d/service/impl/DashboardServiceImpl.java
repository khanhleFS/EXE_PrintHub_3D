package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.custom_prints.CustomOrderDetailResponseDTO;
import com.fpt.printhub_3d.dto.dashboard.DashboardResponseDTO;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.repository.DisputeRepository;
import com.fpt.printhub_3d.repository.UserRepository;
import com.fpt.printhub_3d.service.CustomOrderService;
import com.fpt.printhub_3d.service.DashboardService;
import com.fpt.printhub_3d.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final OrderService orderService;
    private final CustomOrderService customOrderService;
    private final DisputeRepository disputeRepository;
    private final UserRepository userRepository;

    @Override
    public DashboardResponseDTO getDashboard(User user, boolean all) {
        List<OrderResponseDTO> orders = all ? orderService.getAllOrders() : orderService.getMyOrders(user.getId());
        List<CustomOrderDetailResponseDTO> customOrders = all ? customOrderService.getAllCustomOrders() : customOrderService.getMyCustomOrders(user);

        BigDecimal paid = BigDecimal.ZERO;
        long completed = 0;
        int activePrinting = 0;
        List<DashboardResponseDTO.RecentPrintJobDTO> recentJobs = new ArrayList<>();

        for (OrderResponseDTO o : orders) {
            if ("SUCCESS".equals(o.paymentStatus()) || "PAID".equals(o.status()) || "COMPLETED".equals(o.status())) {
                paid = paid.add(o.totalAmount() != null ? o.totalAmount() : BigDecimal.ZERO);
            }
            if ("COMPLETED".equals(o.status())) {
                completed++;
            }
            if ("PREPARING".equals(o.status()) || "PRINTING".equals(o.status())) {
                activePrinting++;
            }

            if (recentJobs.size() < 6 && ("PREPARING".equals(o.status()) || "PRINTING".equals(o.status()) || "PAID".equals(o.status()))) {
                String firstItem = (o.items() != null && !o.items().isEmpty()) ? o.items().get(0).productTitle() : "Đơn hàng gia công 3D";
                recentJobs.add(DashboardResponseDTO.RecentPrintJobDTO.builder()
                        .id(o.id() != null ? o.id().toString().substring(0, 8) : "ORD")
                        .item(firstItem)
                        .factory("Xưởng In PrintHub Trung Tâm")
                        .status("PRINTING".equals(o.status()) ? "Đang in 3D" : "PREPARING".equals(o.status()) ? "Chuẩn bị phôi" : "Đã thanh toán")
                        .type("MARKETPLACE")
                        .price(o.totalAmount())
                        .build());
            }
        }

        for (CustomOrderDetailResponseDTO c : customOrders) {
            if ("PAID".equals(c.status()) || "COMPLETED".equals(c.status())) {
                if (c.quotedPrice() != null) {
                    paid = paid.add(c.quotedPrice());
                }
            }
            if ("COMPLETED".equals(c.status())) {
                completed++;
            }
            if ("PREPARING".equals(c.status()) || "PRINTING".equals(c.status())) {
                activePrinting++;
            }

            if (recentJobs.size() < 6 && ("PREPARING".equals(c.status()) || "PRINTING".equals(c.status()) || "PAID".equals(c.status()) || "ACCEPTED".equals(c.status()))) {
                String itemDesc = (c.rulerModel() != null && !c.rulerModel().isBlank()) ? c.rulerModel() : "Thước 3D Custom";
                if (c.customName() != null && !c.customName().isBlank()) {
                    itemDesc += " (Khắc tên: " + c.customName() + ")";
                }
                recentJobs.add(DashboardResponseDTO.RecentPrintJobDTO.builder()
                        .id(c.id() != null ? c.id().toString().substring(0, 8) : "CUS")
                        .item(itemDesc)
                        .factory("Xưởng Chế Tác Thước Sinh Viên")
                        .status("PRINTING".equals(c.status()) ? "Đang in 3D" : "PREPARING".equals(c.status()) ? "Chuẩn bị phôi & file" : "Chờ in")
                        .type("CUSTOM")
                        .price(c.quotedPrice())
                        .build());
            }
        }

        int pendingDisputes = (int) disputeRepository.countByStatusIn(List.of("OPEN", "PENDING"));
        long totalUsers = userRepository.count();

        return DashboardResponseDTO.builder()
                .orderCount(orders.size())
                .completedCount(completed)
                .paidAmount(paid)
                .customCount(customOrders.size())
                .totalRevenue(paid)
                .activePrintingOrders(activePrinting)
                .pendingDisputesCount(pendingDisputes)
                .totalUsersCount(totalUsers)
                .recentJobs(recentJobs)
                .build();
    }
}
