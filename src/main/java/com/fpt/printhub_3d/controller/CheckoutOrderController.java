package com.fpt.printhub_3d.controller;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.common.util.SecurityUtils;
import com.fpt.printhub_3d.controller.api.CheckoutOrderAPI;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderConfirmRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderCreateRequestDTO;
import com.fpt.printhub_3d.dto.checkout.CheckoutOrderResponseDTO;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.service.CheckoutOrderService;
import com.fpt.printhub_3d.service.PaymentService;
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
    private final PaymentService paymentService;

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CheckoutOrderResponseDTO>> createCheckoutOrder(CheckoutOrderCreateRequestDTO request) {
        User user = SecurityUtils.getCurrentUser();
        CheckoutOrderResponseDTO response = attachPayosLink(user, checkoutOrderService.createCheckoutOrder(user, request));
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
        CheckoutOrderResponseDTO response = attachPayosLink(user, checkoutOrderService.confirmCheckoutOrder(user, id, request));
        return ResponseEntity.ok(ApiResponse.<CheckoutOrderResponseDTO>builder()
                .code(200)
                .message("Xác nhận đơn hàng thành công")
                .result(response)
                .build());
    }

    private CheckoutOrderResponseDTO attachPayosLink(User user, CheckoutOrderResponseDTO response) {
        if ("CONFIRMED".equals(response.status()) && "PAYOS".equals(response.paymentMethod())
                && response.paymentLinkUrl() == null) {
            // The order version and payment code are already committed before contacting PayOS.
            UUID id = response.id();
            try {
                var link = paymentService.createPaymentLink(user.getId(), CreatePaymentLinkRequestDTO.builder()
                        .orderId(id)
                        .orderType("CHECKOUT_ORDER")
                        .paymentOption("FULL")
                        .customAmount(response.totalAmount())
                        .description("Don PrintHub " + id.toString().substring(0, 8))
                        .build());
                response = response.toBuilder().paymentLinkUrl(link.paymentLinkUrl()).build();
            } catch (RuntimeException ex) {
                log.warn("Checkout {} đã xác nhận, chưa lấy được liên kết PayOS: {}", id, ex.getMessage());
            }
        }
        return response;
    }
}
