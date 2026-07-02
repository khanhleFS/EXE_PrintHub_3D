package com.fpt.printhub_3d.controller.api;

import com.fpt.printhub_3d.common.response.ApiResponse;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkResponseDTO;
import com.fpt.printhub_3d.dto.payment.PayOSWebhookRequestDTO;
import com.fpt.printhub_3d.dto.payment.PayOSWebhookResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/payments")
@Tag(name = "Payment APIs", description = "APIs thanh toán qua cổng PayOS — tạo link thanh toán và xử lý webhook IPN")
public interface PaymentAPI {

    @Operation(
            summary = "Tạo link thanh toán PayOS",
            description = "Kết nối gọi sang PayOS khởi tạo link thanh toán, xuất mã QR động tương ứng với số tiền cần thanh toán "
                    + "hoặc đặt cọc (đối với đơn in theo yêu cầu). "
                    + "Đơn hàng ≥ 200.000 VND: đặt cọc 20%. Đơn < 200.000 VND: thanh toán 100%.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/create-link")
    ResponseEntity<ApiResponse<CreatePaymentLinkResponseDTO>> createPaymentLink(
            @Valid @RequestBody CreatePaymentLinkRequestDTO request);

    @Operation(
            summary = "Webhook IPN từ PayOS",
            description = "Lắng nghe tín hiệu IPN tự động từ cổng PayOS để xác nhận giao dịch thành công, "
                    + "chuyển trạng thái đơn hàng và kích hoạt luồng đóng băng/ký quỹ dòng tiền. "
                    + "Endpoint này được gọi bởi hệ thống PayOS, không yêu cầu JWT authentication."
    )
    @PostMapping("/payos-webhook")
    ResponseEntity<ApiResponse<PayOSWebhookResponseDTO>> handlePayOSWebhook(
            @RequestBody PayOSWebhookRequestDTO request);
}
