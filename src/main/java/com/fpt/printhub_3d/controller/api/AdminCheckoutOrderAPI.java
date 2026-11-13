package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.checkout.AdminCheckoutOrderStatusRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/admin/checkout-orders")
@Tag(name = "Admin Checkout Order APIs", description = "APIs for administrators to manage parent checkout orders and sync child print orders")
public interface AdminCheckoutOrderAPI {

    @Operation(
            summary = "List checkout orders (Admin)",
            description = "Lấy danh sách các đơn cha (CheckoutOrder), hỗ trợ lọc theo loại đơn, trạng thái và từ khóa tìm kiếm.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping
    ResponseEntity<ApiResponse<List<CheckoutOrderResponseDTO>>> getAdminCheckoutOrders(
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q
    );

    @Operation(
            summary = "Get checkout order detail (Admin)",
            description = "Xem chi tiết đơn cha và toàn bộ sản phẩm con bên trong.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<CheckoutOrderResponseDTO>> getAdminCheckoutOrder(@PathVariable UUID id);

    @Operation(
            summary = "Batch quote all custom items of a checkout order (Admin)",
            description = "Báo giá từng đơn vị sản phẩm cho toàn bộ các đơn con custom của CheckoutOrder, tự động tính thành tiền, phí ship và tổng đơn.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping("/{id}/quote")
    ResponseEntity<ApiResponse<CheckoutOrderResponseDTO>> batchQuoteCheckoutOrder(
            @PathVariable UUID id,
            @jakarta.validation.Valid @RequestBody com.fpt.printhub_3d.dto.checkout.CheckoutOrderBatchQuoteRequestDTO request
    );

    @Operation(
            summary = "Update checkout order status (Admin)",
            description = "Chuyển trạng thái đơn cha (PREPARING, PRINTING, SHIPPING, COMPLETED, CANCELLED) có kiểm tra thanh toán và đồng bộ xuống các đơn con.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping("/{id}/status")
    ResponseEntity<ApiResponse<CheckoutOrderResponseDTO>> updateAdminCheckoutOrderStatus(
            @PathVariable UUID id,
            @RequestBody AdminCheckoutOrderStatusRequestDTO request
    );
}

