package com.fpt.printhub_3d.service.impl;

import com.fpt.printhub_3d.common.exception.ApiException;
import com.fpt.printhub_3d.common.exception.PaymentErrorCode;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkRequestDTO;
import com.fpt.printhub_3d.dto.payment.CreatePaymentLinkResponseDTO;
import com.fpt.printhub_3d.dto.payment.PayOSWebhookRequestDTO;
import com.fpt.printhub_3d.dto.payment.PayOSWebhookResponseDTO;
import com.fpt.printhub_3d.entity.CustomOrder;
import com.fpt.printhub_3d.entity.Order;
import com.fpt.printhub_3d.entity.Payment;
import com.fpt.printhub_3d.repository.CustomOrderRepository;
import com.fpt.printhub_3d.repository.OrderRepository;
import com.fpt.printhub_3d.repository.PaymentRepository;
import com.fpt.printhub_3d.service.PayOSService;
import com.fpt.printhub_3d.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final CustomOrderRepository customOrderRepository;
    private final PaymentRepository paymentRepository;
    private final PayOSService payOSService;

    // Ngưỡng áp dụng đặt cọc 20% (đơn hàng ≥ 200.000 VND)
    private static final BigDecimal ESCROW_THRESHOLD = new BigDecimal("200000");
    // Tỷ lệ đặt cọc 20%
    private static final BigDecimal DEPOSIT_RATE = new BigDecimal("0.20");

    @Override
    @Transactional
    public CreatePaymentLinkResponseDTO createPaymentLink(UUID userId, CreatePaymentLinkRequestDTO request) {
        log.info("Tạo link thanh toán: userId={}, orderId={}, orderType={}",
                userId, request.orderId(), request.orderType());

        BigDecimal paymentAmount;
        String orderDescription;
        String orderCode;

        if ("ORDER".equals(request.orderType())) {
            // Xử lý đơn hàng thường (marketplace)
            Order order = orderRepository.findById(request.orderId())
                    .orElseThrow(() -> new ApiException(PaymentErrorCode.ORDER_NOT_FOUND));

            // Kiểm tra quyền sở hữu đơn hàng
            if (!order.getBuyer().getId().equals(userId)) {
                throw new ApiException(PaymentErrorCode.ORDER_NOT_OWNED);
            }

            // Kiểm tra trạng thái đơn hàng có thể thanh toán
            if (!"PENDING".equalsIgnoreCase(order.getStatus())) {
                throw new ApiException(PaymentErrorCode.ORDER_NOT_PAYABLE);
            }

            // Kiểm tra đã có payment chưa
            paymentRepository.findByOrderId(order.getId()).ifPresent(existingPayment -> {
                if ("SUCCESS".equalsIgnoreCase(existingPayment.getStatus())) {
                    throw new ApiException(PaymentErrorCode.ORDER_ALREADY_PAID);
                }
            });

            // Tính số tiền thanh toán theo quy tắc escrow:
            // Đơn ≥ 200.000 VND → cọc 20%, đơn < 200.000 VND → thanh toán 100% (COD không áp dụng escrow)
            paymentAmount = calculatePaymentAmount(order.getTotalAmount());
            orderDescription = request.description() != null ? request.description()
                    : "Thanh toán đơn hàng #" + order.getId().toString().substring(0, 8);
            orderCode = "PH3D-" + order.getId().toString().substring(0, 8).toUpperCase();

        } else if ("CUSTOM_ORDER".equals(request.orderType())) {
            // Xử lý đơn in theo yêu cầu (custom print order)
            CustomOrder customOrder = customOrderRepository.findById(request.orderId())
                    .orElseThrow(() -> new ApiException(PaymentErrorCode.ORDER_NOT_FOUND));

            // Kiểm tra quyền sở hữu
            if (!customOrder.getBuyer().getId().equals(userId)) {
                throw new ApiException(PaymentErrorCode.ORDER_NOT_OWNED);
            }

            // Custom order cần ở trạng thái ACCEPTED (đã được Maker báo giá và Buyer chấp nhận)
            if (!"ACCEPTED".equalsIgnoreCase(customOrder.getStatus())) {
                throw new ApiException(PaymentErrorCode.ORDER_NOT_PAYABLE,
                        "Đơn in theo yêu cầu phải ở trạng thái ACCEPTED để thanh toán.");
            }

            // Kiểm tra đã có payment chưa (dùng customOrder id như order id)
            paymentRepository.findByOrderId(customOrder.getId()).ifPresent(existingPayment -> {
                if ("SUCCESS".equalsIgnoreCase(existingPayment.getStatus())) {
                    throw new ApiException(PaymentErrorCode.ORDER_ALREADY_PAID);
                }
            });

            // Đối với custom order, thanh toán theo giá báo giá (đặt cọc theo mô hình escrow)
            if (customOrder.getQuotedPrice() == null) {
                throw new ApiException(PaymentErrorCode.ORDER_NOT_PAYABLE,
                        "Đơn in theo yêu cầu chưa có giá báo giá từ Maker.");
            }
            paymentAmount = calculatePaymentAmount(customOrder.getQuotedPrice());
            orderDescription = request.description() != null ? request.description()
                    : "Đặt cọc đơn in theo yêu cầu #" + customOrder.getId().toString().substring(0, 8);
            orderCode = "PH3D-C-" + customOrder.getId().toString().substring(0, 8).toUpperCase();

        } else {
            throw new ApiException(PaymentErrorCode.INVALID_ORDER_TYPE);
        }

        // Gọi PayOS (mock) để tạo link thanh toán
        CreatePaymentLinkResponseDTO payosResponse = payOSService.createPaymentLink(
                orderCode, paymentAmount, orderDescription);

        // Lưu bản ghi Payment vào DB
        Payment payment = new Payment();
        payment.setAmount(paymentAmount);
        payment.setGateway("PAYOS");
        payment.setStatus("PENDING");
        payment.setTransactionId(orderCode);
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());

        if ("ORDER".equals(request.orderType())) {
            // Đơn hàng thường — liên kết trực tiếp với Order entity
            Order order = orderRepository.findById(request.orderId()).orElse(null);
            payment.setOrder(order);
        } else {
            // Custom order — Payment entity hiện chỉ hỗ trợ liên kết với Order
            // TODO: Mở rộng Payment entity để hỗ trợ cả CustomOrder (thêm custom_order_id nullable)
            // Tạm thời set order = null, cần điều chỉnh constraint Payment.order thành optional
            payment.setOrder(null);
        }

        paymentRepository.save(payment);

        log.info("Đã tạo link thanh toán thành công: orderCode={}, amount={}", orderCode, paymentAmount);
        return payosResponse;
    }

    @Override
    @Transactional
    public PayOSWebhookResponseDTO handleWebhook(PayOSWebhookRequestDTO request) {
        log.info("Nhận webhook từ PayOS: orderCode={}, status={}, transactionId={}",
                request.orderCode(), request.status(), request.transactionId());

        // Xác minh chữ ký webhook
        String rawPayload = request.orderCode() + "|" + request.amount() + "|" + request.status();
        if (!payOSService.verifyWebhookSignature(rawPayload, request.signature())) {
            log.warn("Chữ ký webhook không hợp lệ: orderCode={}", request.orderCode());
            throw new ApiException(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }

        // Tìm Payment record theo transactionId (orderCode)
        Payment payment = paymentRepository.findByTransactionId(request.orderCode())
                .orElseThrow(() -> {
                    log.warn("Không tìm thấy giao dịch: orderCode={}", request.orderCode());
                    return new ApiException(PaymentErrorCode.PAYMENT_NOT_FOUND);
                });

        // Chỉ xử lý nếu payment đang PENDING
        if (!"PENDING".equalsIgnoreCase(payment.getStatus())) {
            log.info("Giao dịch đã được xử lý trước đó: orderCode={}, currentStatus={}",
                    request.orderCode(), payment.getStatus());
            return PayOSWebhookResponseDTO.builder()
                    .success(true)
                    .message("Giao dịch đã được xử lý trước đó.")
                    .build();
        }

        if ("SUCCESS".equalsIgnoreCase(request.status())) {
            // Thanh toán thành công
            payment.setStatus("SUCCESS");
            payment.setPaidAt(Instant.now());
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);

            // Chuyển trạng thái đơn hàng → PAID (kích hoạt luồng ký quỹ/escrow)
            Order order = payment.getOrder();
            if (order != null) {
                order.setStatus("PAID");
                order.setUpdatedAt(Instant.now());
                orderRepository.save(order);
                log.info("Đơn hàng đã chuyển sang trạng thái PAID: orderId={}", order.getId());
            }

            // TODO: Kích hoạt luồng đóng băng/ký quỹ dòng tiền (escrow freeze)
            // - Đóng băng số tiền trong ví trung gian (pending_balance)
            // - Gửi notification cho Maker bắt đầu xử lý đơn
            log.info("TODO: Kích hoạt luồng escrow freeze cho orderCode={}", request.orderCode());

            return PayOSWebhookResponseDTO.builder()
                    .success(true)
                    .message("Thanh toán thành công. Đơn hàng đã được cập nhật.")
                    .build();

        } else if ("FAILED".equalsIgnoreCase(request.status()) || "CANCELLED".equalsIgnoreCase(request.status())) {
            // Thanh toán thất bại hoặc bị hủy
            payment.setStatus("FAILED");
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);

            log.info("Thanh toán thất bại/hủy: orderCode={}, status={}", request.orderCode(), request.status());

            return PayOSWebhookResponseDTO.builder()
                    .success(true)
                    .message("Đã ghi nhận giao dịch thất bại.")
                    .build();
        }

        log.warn("Trạng thái webhook không xác định: status={}", request.status());
        return PayOSWebhookResponseDTO.builder()
                .success(false)
                .message("Trạng thái giao dịch không được hỗ trợ: " + request.status())
                .build();
    }

    // ──────────────────────────── Private Helpers ────────────────────────────

    /**
     * Tính số tiền cần thanh toán dựa trên quy tắc escrow:
     * - Đơn hàng ≥ 200.000 VND → đặt cọc 20%
     * - Đơn hàng < 200.000 VND → thanh toán 100% (COD giảm ma sát)
     */
    private BigDecimal calculatePaymentAmount(BigDecimal totalAmount) {
        if (totalAmount.compareTo(ESCROW_THRESHOLD) >= 0) {
            // Đặt cọc 20% giá trị đơn hàng
            BigDecimal deposit = totalAmount.multiply(DEPOSIT_RATE).setScale(0, RoundingMode.CEILING);
            log.info("Áp dụng đặt cọc 20%: totalAmount={}, deposit={}", totalAmount, deposit);
            return deposit;
        }
        // Thanh toán 100%
        log.info("Thanh toán 100% (đơn < 200.000 VND): totalAmount={}", totalAmount);
        return totalAmount;
    }
}
