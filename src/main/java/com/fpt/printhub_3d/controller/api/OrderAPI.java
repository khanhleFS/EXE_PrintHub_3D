package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.order.OrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.order.OrderResponseDTO;
import com.fpt.printhub_3d.dto.order.OrderStatusUpdateRequestDTO;
import com.fpt.printhub_3d.dto.order.RewardCompletionResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/orders")
@Tag(name = "Order APIs", description = "APIs for creating and managing orders")
public interface OrderAPI {

    @Operation(
            summary = "Create new marketplace order(s)",
            description = "Khởi tạo đơn hàng mua các sản phẩm.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping
    ResponseEntity<ApiResponse<List<OrderResponseDTO>>> createOrders(
            @Valid @RequestBody OrderCreateRequestDTO request);

    @Operation(
            summary = "Complete reward points for a completed order",
            description = "Tự động tính và cộng điểm thưởng khi đơn hoàn thành.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/{id}/complete-rewards")
    ResponseEntity<ApiResponse<RewardCompletionResponseDTO>> completeRewards(@PathVariable UUID id);

    @Operation(
            summary = "Get current user's order history",
            description = "Lấy lịch sử đơn hàng của người dùng.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/my-orders")
    ResponseEntity<ApiResponse<List<OrderResponseDTO>>> getMyOrders();

    @Operation(
            summary = "Get current user's orders (alias /me)",
            description = "Lấy đơn hàng của người dùng hiện tại.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/me")
    ResponseEntity<ApiResponse<List<OrderResponseDTO>>> getMyOrdersAlt();

    @Operation(
            summary = "Get order details by ID",
            description = "Xem chi tiết một đơn hàng theo ID.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<OrderResponseDTO>> getOrderById(@PathVariable UUID id);

    @Operation(
            summary = "Update order status",
            description = "Cập nhật trạng thái đơn hàng (ví dụ: người mua hủy đơn, admin đổi tiến độ).",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping("/{id}/status")
    ResponseEntity<ApiResponse<Void>> updateOrderStatus(
            @PathVariable UUID id,
            @Valid @RequestBody OrderStatusUpdateRequestDTO request);
}
