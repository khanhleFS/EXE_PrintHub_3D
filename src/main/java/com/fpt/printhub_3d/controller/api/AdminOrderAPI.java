package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RequestMapping("/api/admin/orders")
@Tag(name = "Admin Order APIs", description = "APIs for administrator to view and manage all orders")
public interface AdminOrderAPI {

    @Operation(
            summary = "Get all orders (Admin)",
            description = "Quản trị viên xem danh sách toàn bộ các đơn hàng trong hệ thống.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping
    ResponseEntity<ApiResponse<List<OrderResponseDTO>>> getAllOrders();
}
