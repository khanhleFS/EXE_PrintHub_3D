package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.security.CustomUserDetail;
import com.fpt.printhub_3d.controller.api.PaymentAPI;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkResponseDTO;
import com.fpt.printhub_3d.dto.payment.PayOSWebhookRequestDTO;
import com.fpt.printhub_3d.dto.payment.PayOSWebhookResponseDTO;
import com.fpt.printhub_3d.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin("*")
public class PaymentController implements PaymentAPI {

    private final PaymentService paymentService;

    @Override
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CreatePaymentLinkResponseDTO>> createPaymentLink(
            CreatePaymentLinkRequestDTO request) {
        // Lấy thông tin user hiện tại từ SecurityContext
        CustomUserDetail userDetail = (CustomUserDetail) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();

        CreatePaymentLinkResponseDTO response = paymentService.createPaymentLink(
                userDetail.getUser().getId(), request);

        return ResponseEntity.ok(ApiResponse.<CreatePaymentLinkResponseDTO>builder()
                .code(200)
                .message("Tạo link thanh toán thành công")
                .result(response)
                .build());
    }

    @Override
    public ResponseEntity<ApiResponse<PayOSWebhookResponseDTO>> handlePayOSWebhook(
            PayOSWebhookRequestDTO request) {
        // Webhook từ PayOS — không yêu cầu authentication (đã permit trong SecurityConfig)
        log.info("Nhận webhook từ PayOS: orderCode={}", request.orderCode());

        PayOSWebhookResponseDTO response = paymentService.handleWebhook(request);

        return ResponseEntity.ok(ApiResponse.<PayOSWebhookResponseDTO>builder()
                .code(200)
                .message("Webhook đã được xử lý")
                .result(response)
                .build());
    }
}
