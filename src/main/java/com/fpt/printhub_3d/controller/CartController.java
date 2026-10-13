package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.CartAPI;
import com.fpt.printhub_3d.dto.cart.CartItemResponseDTO;
import com.fpt.printhub_3d.dto.cart.CartItemUpdateRequestDTO;
import com.fpt.printhub_3d.dto.cart.CartUpdateRequestDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class CartController implements CartAPI {

    private final CartService cartService;

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CartItemResponseDTO>>> getCart() {
        User user = SecurityUtils.getCurrentUser();
        List<CartItemResponseDTO> items = cartService.getCart(user);
        return ResponseEntity.ok(ApiResponse.<List<CartItemResponseDTO>>builder()
                .code(200)
                .message("Lấy giỏ hàng thành công")
                .result(items)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CartItemResponseDTO>>> updateCart(CartUpdateRequestDTO request) {
        User user = SecurityUtils.getCurrentUser();
        List<CartItemResponseDTO> items = cartService.updateCart(user, request);
        return ResponseEntity.ok(ApiResponse.<List<CartItemResponseDTO>>builder()
                .code(200)
                .message("Cập nhật giỏ hàng thành công")
                .result(items)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CartItemResponseDTO>> updateCartItem(UUID clientLineId, CartItemUpdateRequestDTO request) {
        User user = SecurityUtils.getCurrentUser();
        CartItemResponseDTO updated = cartService.updateCartItem(user, clientLineId, request);
        return ResponseEntity.ok(ApiResponse.<CartItemResponseDTO>builder()
                .code(200)
                .message("Cập nhật dòng sản phẩm thành công")
                .result(updated)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCartItem(UUID clientLineId) {
        User user = SecurityUtils.getCurrentUser();
        cartService.deleteCartItem(user, clientLineId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Xóa dòng sản phẩm khỏi giỏ hàng thành công")
                .build());
    }
}
