package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.OrderAPI;
import com.fpt.printhub_3d.dto.order.OrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import com.fpt.printhub_3d.dto.order.OrderStatusUpdateRequestDTO;
import com.fpt.printhub_3d.dto.order.RewardCompletionResponseDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
public class OrderController implements OrderAPI {

    private final OrderService orderService;

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderResponseDTO>>> createOrders(OrderCreateRequestDTO request) {
        User buyer = SecurityUtils.getCurrentUser();
        List<OrderResponseDTO> response = orderService.createOrders(request, buyer);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<List<OrderResponseDTO>>builder()
                        .code(201)
                        .message("Khởi tạo đơn hàng thành công")
                        .result(response)
                        .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RewardCompletionResponseDTO>> completeRewards(UUID id) {
        RewardCompletionResponseDTO response = orderService.completeRewards(id);
        return ResponseEntity.ok(ApiResponse.<RewardCompletionResponseDTO>builder()
                .code(200)
                .message("Xử lý điểm thưởng thành công")
                .result(response)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderResponseDTO>>> getMyOrders() {
        User buyer = SecurityUtils.getCurrentUser();
        List<OrderResponseDTO> response = orderService.getMyOrders(buyer.getId());
        return ResponseEntity.ok(ApiResponse.<List<OrderResponseDTO>>builder()
                .code(200)
                .message("Lấy lịch sử đơn hàng thành công")
                .result(response)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderResponseDTO>>> getMyOrdersAlt() {
        return getMyOrders();
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponseDTO>> getOrderById(UUID id) {
        User user = SecurityUtils.getCurrentUser();
        OrderResponseDTO response = orderService.getOrderById(id, user);
        return ResponseEntity.ok(ApiResponse.<OrderResponseDTO>builder()
                .code(200)
                .message("Lấy chi tiết đơn hàng thành công")
                .result(response)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateOrderStatus(UUID id, OrderStatusUpdateRequestDTO request) {
        User user = SecurityUtils.getCurrentUser();
        orderService.updateOrderStatus(id, request.status(), user);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Cập nhật trạng thái đơn hàng thành công")
                .build());
    }
}
