package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderConfirmRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/checkout-orders")
@Tag(name = "Checkout Order APIs", description = "APIs quản lý đơn hàng thanh toán cha (hỗn hợp Marketplace và Custom)")
public interface CheckoutOrderAPI {

    @Operation(
            summary = "Create parent checkout order",
            description = "Tạo đơn hàng thanh toán cha từ các dòng được chọn trong giỏ hàng.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping
    ResponseEntity<ApiResponse<CheckoutOrderResponseDTO>> createCheckoutOrder(
            @Valid @RequestBody CheckoutOrderCreateRequestDTO request);

    @Operation(
            summary = "Get checkout order details",
            description = "Lấy thông tin chi tiết đơn cha, tiến độ báo giá custom và các đơn marketplace thành phần.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<CheckoutOrderResponseDTO>> getCheckoutOrder(
            @PathVariable("id") UUID id);

    @Operation(
            summary = "Get my checkout orders",
            description = "Lấy danh sách các đơn thanh toán cha của người dùng hiện tại.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/my")
    ResponseEntity<ApiResponse<List<CheckoutOrderResponseDTO>>> getMyCheckoutOrders();

    @Operation(
            summary = "Confirm checkout order",
            description = "Khách hàng duyệt báo giá, kiểm tra giá và tồn kho Marketplace để kích hoạt đơn và thanh toán.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/{id}/confirm")
    ResponseEntity<ApiResponse<CheckoutOrderResponseDTO>> confirmCheckoutOrder(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CheckoutOrderConfirmRequestDTO request);
}
