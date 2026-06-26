package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.security.CustomUserDetail;
import com.fpt.printhub_3d.controller.api.OrderAPI;
import com.fpt.printhub_3d.dto.order.OrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import com.fpt.printhub_3d.dto.order.RewardCompletionResponseDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class OrderController implements OrderAPI {

    private final OrderService orderService;

    @Override
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<OrderResponseDTO>>> createOrders(OrderCreateRequestDTO request) {
        CustomUserDetail userDetail = (CustomUserDetail) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        User buyer = userDetail.getUser();

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
                .message("Cộng điểm thưởng cho đơn hàng thành công")
                .result(response)
                .build());
    }
}
