package com.fpt.printhub_3d.service;

import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkResponseDTO;
import com.fpt.printhub_3d.dto.payment.PayOSWebhookRequestDTO;
import com.fpt.printhub_3d.dto.payment.PayOSWebhookResponseDTO;

import java.util.UUID;

/**
 * Service xử lý nghiệp vụ thanh toán — tạo link PayOS và xử lý webhook IPN.
 */
public interface PaymentService {

    /**
     * Tạo link thanh toán PayOS cho đơn hàng.
     * Tính số tiền dựa trên loại đơn: 20% cọc cho đơn ≥200.000 VND, 100% cho đơn <200.000 VND.
     * Đối với đơn CUSTOM_ORDER, thanh toán 100% giá trị báo giá (đặt cọc theo yêu cầu).
     *
     * @param userId  ID người dùng hiện tại (CUSTOMER)
     * @param request thông tin đơn hàng cần thanh toán
     * @return link thanh toán và mã QR
     */
    CreatePaymentLinkResponseDTO createPaymentLink(UUID userId, CreatePaymentLinkRequestDTO request);

    /**
     * Xử lý webhook IPN từ PayOS.
     * Xác minh chữ ký, cập nhật trạng thái Payment, chuyển trạng thái đơn hàng,
     * kích hoạt luồng đóng băng/ký quỹ dòng tiền.
     *
     * @param request payload webhook từ PayOS
     * @return kết quả xử lý webhook
     */
    PayOSWebhookResponseDTO handleWebhook(PayOSWebhookRequestDTO request);
}
