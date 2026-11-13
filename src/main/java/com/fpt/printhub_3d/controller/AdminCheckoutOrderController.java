package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.AdminCheckoutOrderAPI;
import com.fpt.printhub_3d.dto.checkout.AdminCheckoutOrderStatusRequestDTO;
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
public class AdminCheckoutOrderController implements AdminCheckoutOrderAPI {

    private final CheckoutOrderService checkoutOrderService;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CheckoutOrderResponseDTO>>> getAdminCheckoutOrders(
            String orderType,
            String status,
            String q
    ) {
        List<CheckoutOrderResponseDTO> result = checkoutOrderService.getAdminCheckoutOrders(orderType, status, q);
        return ResponseEntity.ok(ApiResponse.<List<CheckoutOrderResponseDTO>>builder()
                .code(200)
                .message("Lấy danh sách đơn cha thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CheckoutOrderResponseDTO>> getAdminCheckoutOrder(UUID id) {
        CheckoutOrderResponseDTO result = checkoutOrderService.getAdminCheckoutOrder(id);
        return ResponseEntity.ok(ApiResponse.<CheckoutOrderResponseDTO>builder()
                .code(200)
                .message("Lấy chi tiết đơn cha thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CheckoutOrderResponseDTO>> updateAdminCheckoutOrderStatus(
            UUID id,
            AdminCheckoutOrderStatusRequestDTO request
    ) {
        User admin = SecurityUtils.getCurrentUser();
        CheckoutOrderResponseDTO result = checkoutOrderService.updateAdminCheckoutOrderStatus(admin, id, request);
        return ResponseEntity.ok(ApiResponse.<CheckoutOrderResponseDTO>builder()
                .code(200)
                .message("Cập nhật trạng thái đơn cha thành công")
                .result(result)
                .build());
    }
}

