package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.cart.CartItemResponseDTO;
import com.fpt.printhub_3d.dto.cart.CartUpdateRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RequestMapping("/api/cart")
@Tag(name = "Cart APIs", description = "APIs for shopping cart management")
public interface CartAPI {

    @Operation(
            summary = "Get user shopping cart",
            description = "Lấy danh sách các sản phẩm đang có trong giỏ hàng của người dùng hiện tại.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping
    ResponseEntity<ApiResponse<List<CartItemResponseDTO>>> getCart();

    @Operation(
            summary = "Update shopping cart",
            description = "Cập nhật hoặc đồng bộ toàn bộ sản phẩm trong giỏ hàng.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PutMapping
    ResponseEntity<ApiResponse<List<CartItemResponseDTO>>> updateCart(
            @Valid @RequestBody CartUpdateRequestDTO request);
}
