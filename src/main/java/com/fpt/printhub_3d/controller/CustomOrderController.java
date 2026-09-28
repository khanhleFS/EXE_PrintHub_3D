package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.CustomOrderAPI;
import com.fpt.printhub_3d.dto.custom_prints.*;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.CustomOrderService;
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
public class CustomOrderController implements CustomOrderAPI {

    private final CustomOrderService customOrderService;

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CustomOrderDetailResponseDTO>>> getMyCustomOrders() {
        User user = SecurityUtils.getCurrentUser();
        List<CustomOrderDetailResponseDTO> result = customOrderService.getMyCustomOrders(user);
        return ResponseEntity.ok(ApiResponse.<List<CustomOrderDetailResponseDTO>>builder()
                .code(200)
                .message("Lấy danh sách yêu cầu in thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CustomOrderDetailResponseDTO>>> getAllCustomOrders() {
        List<CustomOrderDetailResponseDTO> result = customOrderService.getAllCustomOrders();
        return ResponseEntity.ok(ApiResponse.<List<CustomOrderDetailResponseDTO>>builder()
                .code(200)
                .message("Lấy toàn bộ yêu cầu in thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CustomOrderDetailResponseDTO>> createCustomOrder(CustomOrderCreateRequestDTO request) {
        User user = SecurityUtils.getCurrentUser();
        CustomOrderDetailResponseDTO result = customOrderService.createCustomOrder(user, request);
        return ResponseEntity.ok(ApiResponse.<CustomOrderDetailResponseDTO>builder()
                .code(200)
                .message("Tạo yêu cầu in thành công")
                .result(result)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> quoteCustomOrder(UUID id, CustomOrderQuoteRequestDTO request) {
        User maker = SecurityUtils.getCurrentUser();
        customOrderService.quoteCustomOrder(id, request, maker);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Gửi báo giá thành công")
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateCustomOrderStatus(UUID id, CustomOrderStatusRequestDTO request) {
        User user = SecurityUtils.getCurrentUser();
        customOrderService.updateCustomOrderStatus(id, request, user);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Cập nhật trạng thái thành công")
                .build());
    }
}
