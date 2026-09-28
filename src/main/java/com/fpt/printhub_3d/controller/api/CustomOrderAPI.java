package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.custom_prints.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Custom Order Workflow APIs", description = "APIs for custom 3D printing quotation and progress tracking")
public interface CustomOrderAPI {

    @Operation(
            summary = "Get user's custom print requests",
            description = "Lấy danh sách các yêu cầu in tùy chỉnh của người dùng hiện tại.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/api/custom-orders")
    ResponseEntity<ApiResponse<List<CustomOrderDetailResponseDTO>>> getMyCustomOrders();

    @Operation(
            summary = "Get all custom print requests (Admin)",
            description = "Quản trị viên / Maker xem toàn bộ yêu cầu in tùy chỉnh.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/api/admin/custom-orders")
    ResponseEntity<ApiResponse<List<CustomOrderDetailResponseDTO>>> getAllCustomOrders();

    @Operation(
            summary = "Create custom print request",
            description = "Người dùng gửi yêu cầu in 3D kèm thông số từ tệp đã upload.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/api/custom-orders")
    ResponseEntity<ApiResponse<CustomOrderDetailResponseDTO>> createCustomOrder(
            @Valid @RequestBody CustomOrderCreateRequestDTO request);

    @Operation(
            summary = "Quote custom order (Admin/Maker)",
            description = "Báo giá cho yêu cầu in 3D.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping("/api/admin/custom-orders/{id}/quote")
    ResponseEntity<ApiResponse<Void>> quoteCustomOrder(
            @PathVariable UUID id,
            @Valid @RequestBody CustomOrderQuoteRequestDTO request);

    @Operation(
            summary = "Update custom order status",
            description = "Cập nhật trạng thái yêu cầu in (chấp nhận báo giá, hủy, in, giao hàng).",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping("/api/custom-orders/{id}/status")
    ResponseEntity<ApiResponse<Void>> updateCustomOrderStatus(
            @PathVariable UUID id,
            @Valid @RequestBody CustomOrderStatusRequestDTO request);
}
