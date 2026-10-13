package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.CheckoutOrderAPI;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderConfirmRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderResponseDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.CheckoutOrderService;
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
public class CheckoutOrderController implements CheckoutOrderAPI {

    private final CheckoutOrderService checkoutOrderService;

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CheckoutOrderResponseDTO>> createCheckoutOrder(CheckoutOrderCreateRequestDTO request) {
        User user = SecurityUtils.getCurrentUser();
        CheckoutOrderResponseDTO response = checkoutOrderService.createCheckoutOrder(user, request);
        return ResponseEntity.ok(ApiResponse.<CheckoutOrderResponseDTO>builder()
                .code(200)
                .message("Tạo đơn hàng thành công")
                .result(response)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CheckoutOrderResponseDTO>> getCheckoutOrder(UUID id) {
        User user = SecurityUtils.getCurrentUser();
        CheckoutOrderResponseDTO response = checkoutOrderService.getCheckoutOrder(user, id);
        return ResponseEntity.ok(ApiResponse.<CheckoutOrderResponseDTO>builder()
                .code(200)
                .message("Lấy thông tin đơn hàng thành công")
                .result(response)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CheckoutOrderResponseDTO>>> getMyCheckoutOrders() {
        User user = SecurityUtils.getCurrentUser();
        List<CheckoutOrderResponseDTO> response = checkoutOrderService.getMyCheckoutOrders(user);
        return ResponseEntity.ok(ApiResponse.<List<CheckoutOrderResponseDTO>>builder()
                .code(200)
                .message("Lấy danh sách đơn hàng thành công")
                .result(response)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CheckoutOrderResponseDTO>> confirmCheckoutOrder(UUID id, CheckoutOrderConfirmRequestDTO request) {
        User user = SecurityUtils.getCurrentUser();
        CheckoutOrderResponseDTO response = checkoutOrderService.confirmCheckoutOrder(user, id, request);
        return ResponseEntity.ok(ApiResponse.<CheckoutOrderResponseDTO>builder()
                .code(200)
                .message("Xác nhận đơn hàng thành công")
                .result(response)
                .build());
    }
}
