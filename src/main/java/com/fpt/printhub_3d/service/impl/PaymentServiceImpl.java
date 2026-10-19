package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkResponseDTO;
import com.fpt.printhub_3d.dto.payment.PayOSWebhookResponseDTO;
import com.fpt.printhub_3d.dto.payment.PaymentAttemptStatusResponseDTO;
import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.repository.*;
import com.fpt.printhub_3d.service.NotificationService;
import com.fpt.printhub_3d.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orders;
    private final CustomOrderRepository customs;
    private final CheckoutOrderRepository checkoutOrders;
    private final PaymentRepository payments;
    private final PayOS payOS;
    private final NotificationService notifications;
    private final InventoryReservationRepository inventoryReservations;
    private final ProductRepository products;
    private final PaymentAttemptRepository paymentAttempts;
    private final PaymentAttemptHelper paymentAttemptHelper;
    private final PaymentTransactionStepService paymentTransactionStepService;

    @Value("${payos.return-url:http://localhost:5173/payment-result}")
    private String returnUrl;

    @Value("${payos.cancel-url:http://localhost:5173/payment-result?cancel=true}")
    private String cancelUrl;

    @Override
    public CreatePaymentLinkResponseDTO createPaymentLink(UUID userId, CreatePaymentLinkRequestDTO request) {
        // Phase 1 (Tx 1): Lock, revalidate, claim or reuse attempt
        PaymentTransactionStepService.PrepareAttemptResult prepared = paymentTransactionStepService.preparePaymentAttempt(userId, request);

        if (prepared.ready()) {
            return CreatePaymentLinkResponseDTO.builder()
                    .paymentLinkUrl(prepared.checkoutUrl())
                    .qrCodeUrl(prepared.qrCode())
                    .amount(prepared.amount())
                    .orderCode(String.valueOf(prepared.providerCode()))
                    .expiredAt(prepared.expiresAt())
                    .attemptId(prepared.attemptId())
                    .status("READY")
                    .build();
        }

        if (prepared.initializing()) {
            return CreatePaymentLinkResponseDTO.builder()
                    .amount(prepared.amount())
                    .orderCode(String.valueOf(prepared.providerCode()))
                    .attemptId(prepared.attemptId())
                    .status("INITIALIZING")
                    .retryAfterSeconds(prepared.retryAfterSeconds() != null ? prepared.retryAfterSeconds() : 2)
                    .build();
        }

        // P1.1: Nếu attempt cũ có providerCode đang dở dang hoặc PENDING_VERIFY:
        // Phải tra cứu PayOS trước để khôi phục link cũ hoặc chốt trạng thái, tuyệt đối không tạo link mới ngay
        if (prepared.needsVerification()) {
            log.info("R2-05 P1.1: Tra cứu PayOS cho attempt dở dang orderCode={} attemptId={}",
                    prepared.providerCode(), prepared.attemptId());
            try {
                var live = payOS.paymentRequests().get(prepared.providerCode());
                if (live != null) {
                    String liveStatus = live.getStatus() != null ? String.valueOf(live.getStatus()) : "UNKNOWN";
                    if ("PAID".equals(liveStatus)) {
                        paymentTransactionStepService.settlePayment(prepared.providerCode(), BigDecimal.valueOf(live.getAmountPaid()));
                        return CreatePaymentLinkResponseDTO.builder()
                                .amount(prepared.amount())
                                .orderCode(String.valueOf(prepared.providerCode()))
                                .attemptId(prepared.attemptId())
                                .status("SUCCESS")
                                .build();
                    } else if ("PENDING".equals(liveStatus) || "PROCESSING".equals(liveStatus)) {
                        // Link đã tồn tại trên PayOS! Khôi phục URL và trả về cho client
                        String recoveredUrl = (prepared.checkoutUrl() != null && !prepared.checkoutUrl().isBlank())
                                ? prepared.checkoutUrl() : "https://pay.payos.vn/web/" + live.getId();
                        String recoveredQr = (prepared.qrCode() != null && !prepared.qrCode().isBlank())
                                ? prepared.qrCode() : recoveredUrl;
                        paymentTransactionStepService.finalizePaymentAttempt(
                                prepared.attemptId(), prepared.providerCode(), recoveredUrl, recoveredQr
                        );
                        return CreatePaymentLinkResponseDTO.builder()
                                .paymentLinkUrl(recoveredUrl)
                                .qrCodeUrl(recoveredQr)
                                .amount(prepared.amount())
                                .orderCode(String.valueOf(prepared.providerCode()))
                                .expiredAt(prepared.expiresAt())
                                .attemptId(prepared.attemptId())
                                .status("READY")
                                .build();
                    } else if ("CANCELLED".equals(liveStatus) || "EXPIRED".equals(liveStatus)) {
                        paymentTransactionStepService.markAttemptExpired(prepared.attemptId());
                        prepared = paymentTransactionStepService.preparePaymentAttempt(userId, request);
                    }
                }
            } catch (Exception ex) {
                String exMsg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
                boolean isDefinitiveNotFound = exMsg.contains("\"code\":\"20\"")
                        || exMsg.contains("\"code\": 20")
                        || exMsg.contains("đơn hàng không tồn tại")
                        || exMsg.contains("payment request not found");
                boolean isRecent = prepared.expiresAt() != null && prepared.expiresAt().isAfter(Instant.now().plusSeconds(870));
                if (isDefinitiveNotFound && !isRecent) {
                    // Cổng PayOS chưa từng ghi nhận orderCode này và attempt đã đủ trễ -> Chuyển attempt cũ thành EXPIRED và tạo attempt mới
                    paymentTransactionStepService.markAttemptExpired(prepared.attemptId());
                    prepared = paymentTransactionStepService.preparePaymentAttempt(userId, request);
                } else {
                    // PayOS timeout, lỗi kết nối hoặc attempt vừa tạo: KHÔNG được sinh mã mới! Trả về HTTP 202/INITIALIZING để client poll
                    log.warn("Chưa thể xác minh PayOS cho attempt orderCode={}: {}. Trả về INITIALIZING để tiếp tục polling",
                            prepared.providerCode(), ex.getMessage());
                    return CreatePaymentLinkResponseDTO.builder()
                            .amount(prepared.amount())
                            .orderCode(String.valueOf(prepared.providerCode()))
                            .attemptId(prepared.attemptId())
                            .status("INITIALIZING")
                            .retryAfterSeconds(3)
                            .build();
                }
            }

            if (prepared.ready()) {
                return CreatePaymentLinkResponseDTO.builder()
                        .paymentLinkUrl(prepared.checkoutUrl())
                        .qrCodeUrl(prepared.qrCode())
                        .amount(prepared.amount())
                        .orderCode(String.valueOf(prepared.providerCode()))
                        .expiredAt(prepared.expiresAt())
                        .attemptId(prepared.attemptId())
                        .status("READY")
                        .build();
            }
            if (prepared.initializing()) {
                return CreatePaymentLinkResponseDTO.builder()
                        .amount(prepared.amount())
                        .orderCode(String.valueOf(prepared.providerCode()))
                        .attemptId(prepared.attemptId())
                        .status("INITIALIZING")
                        .retryAfterSeconds(2)
                        .build();
            }
        }

        // Phase 2: Call network provider PayOS OUTSIDE any database transaction
        String finalReturnUrl = (request.returnUrl() != null && !request.returnUrl().isBlank())
                ? request.returnUrl() : returnUrl;
        String finalCancelUrl = (request.cancelUrl() != null && !request.cancelUrl().isBlank())
                ? request.cancelUrl() : cancelUrl;

        String description = (request.description() != null && !request.description().isBlank())
                ? request.description()
                : "PrintHub " + prepared.providerCode();
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        CreatePaymentLinkResponse link;
        try {
            link = payOS.paymentRequests().create(CreatePaymentLinkRequest.builder()
                    .orderCode(prepared.providerCode())
                    .amount(prepared.amount().setScale(0, java.math.RoundingMode.HALF_UP).longValue())
                    .description(description)
                    .returnUrl(finalReturnUrl.replace("/#/", "/"))
                    .cancelUrl(finalCancelUrl.replace("/#/", "/"))
                    .expiredAt(prepared.expiresAt().getEpochSecond())
                    .build());
        } catch (Exception e) {
            log.error("Lỗi khi gọi PayOS tạo link thanh toán orderCode {}: {}", prepared.providerCode(), e.getMessage());
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            boolean isTimeout = errorMsg.contains("timeout") || errorMsg.contains("timed out")
                    || errorMsg.contains("connection reset") || errorMsg.contains("read timeout")
                    || errorMsg.contains("connect timeout") || (e instanceof java.net.SocketTimeoutException);
            if (isTimeout) {
                paymentTransactionStepService.recordAttemptTimeout(prepared.providerCode(), e.getMessage());
                throw bad("Kết nối PayOS bị gián đoạn/timeout. Vui lòng bấm thử lại để kiểm tra liên kết thanh toán.");
            } else {
                paymentTransactionStepService.recordAttemptFailed(prepared.providerCode(), e.getMessage());
                throw bad("Không thể tạo liên kết thanh toán PayOS: " + e.getMessage());
            }
        }

        // Phase 3 (Tx 2): Update checkoutUrl and qrCode independently
        paymentTransactionStepService.finalizePaymentAttempt(
                prepared.attemptId(), prepared.providerCode(), link.getCheckoutUrl(), link.getQrCode()
        );

        return CreatePaymentLinkResponseDTO.builder()
                .paymentLinkUrl(link.getCheckoutUrl())
                .qrCodeUrl(link.getQrCode())
                .amount(prepared.amount())
                .orderCode(String.valueOf(prepared.providerCode()))
                .expiredAt(prepared.expiresAt())
                .attemptId(prepared.attemptId())
                .status("READY")
                .build();
    }

    @Override
    public PaymentAttemptStatusResponseDTO getAttemptStatus(UUID attemptId, UUID userId) {
        return paymentTransactionStepService.getAttemptStatus(attemptId, userId);
    }

    @Override
    @Transactional
    public PayOSWebhookResponseDTO handleWebhook(Webhook request) {
        WebhookData data;
        try {
            data = payOS.webhooks().verify(request);
        } catch (Exception e) {
            throw bad("Chữ ký webhook không hợp lệ");
        }

        if (!"00".equals(data.getCode())) {
            return PayOSWebhookResponseDTO.builder()
                    .success(true)
                    .message("Đã nhận thông báo chưa thanh toán")
                    .build();
        }

        var existing = payments.findByTransactionId(String.valueOf(data.getOrderCode()));
        if (existing.isEmpty()) {
            var attemptOpt = paymentAttempts.findByProviderCode(data.getOrderCode());
            if (attemptOpt.isEmpty()) {
                return PayOSWebhookResponseDTO.builder()
                        .success(true)
                        .message("Webhook kiểm tra từ PayOS hoặc giao dịch không tồn tại: " + data.getOrderCode())
                        .build();
            }
        }

        if (data.getAmount() == null) {
            throw bad("Thiếu số tiền");
        }

        paymentTransactionStepService.settlePayment(data.getOrderCode(), BigDecimal.valueOf(data.getAmount()));

        return PayOSWebhookResponseDTO.builder()
                .success(true)
                .message("Đã xác nhận thanh toán")
                .build();
    }

    @Override
    @Transactional
    public Map<String, Object> verify(String code, UUID userId) {
        Payment payment = lockPayment(code);
        if (!owner(payment).equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (!"SUCCESS".equals(payment.getStatus())) {
            try {
                var live = payOS.paymentRequests().get(Long.valueOf(code));
                String liveStatus = live != null ? String.valueOf(live.getStatus()) : "UNKNOWN";

                if ("PAID".equals(liveStatus)) {
                    paymentTransactionStepService.settlePayment(Long.valueOf(code), BigDecimal.valueOf(live.getAmountPaid()));
                } else if ("CANCELLED".equals(liveStatus)) {
                    payment.setStatus("CANCELLED");
                    payment.setUpdatedAt(Instant.now());
                    payments.save(payment);
                }
            } catch (Exception ignored) {
                // Tránh lỗi 500 nếu PayOS gặp gián đoạn tạm thời
            }
        }

        return Map.of(
                "orderCode", code,
                "status", "SUCCESS".equals(payment.getStatus()) ? "PAID" : payment.getStatus(),
                "amount", payment.getAmount()
        );
    }

    // --- Private Helper Methods ---

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private Payment lockPayment(String code) {
        return paymentTransactionStepService.lockPayment(code);
    }

    private UUID owner(Payment payment) {
        if (payment.getOrder() != null) {
            return payment.getOrder().getBuyer().getId();
        } else if (payment.getCustomOrder() != null) {
            return payment.getCustomOrder().getBuyer().getId();
        } else if (payment.getCheckoutOrder() != null) {
            return payment.getCheckoutOrder().getUser().getId();
        }
        throw bad("Giao dịch không có thông tin người sở hữu");
    }

    @Transactional
    public void settle(Payment payment, BigDecimal amount) {
        Long code = null;
        try {
            code = Long.valueOf(payment.getTransactionId());
        } catch (Exception ignored) {
        }
        settle(payment, amount, code);
    }

    @Transactional
    public void settle(Payment payment, BigDecimal amount, Long providerCode) {
        paymentTransactionStepService.settle(payment, amount, providerCode);
    }

    private CreatePaymentLinkResponseDTO response(Payment payment) {
        return CreatePaymentLinkResponseDTO.builder()
                .paymentLinkUrl(payment.getCheckoutUrl())
                .orderCode(payment.getTransactionId())
                .amount(payment.getAmount())
                .expiredAt(payment.getExpiresAt())
                .build();
    }
}