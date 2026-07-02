package com.fpt.printhub_3d.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Error codes cho domain Payment & Escrow.
 *
 * Error Code Convention:
 * - 40xxx: Payment domain errors
 */
@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    // 40xxx - Payment Client Errors
    ORDER_NOT_FOUND(40001, "Không tìm thấy đơn hàng", HttpStatus.NOT_FOUND, "payment.order_not_found"),
    ORDER_NOT_OWNED(40002, "Bạn không có quyền thanh toán đơn hàng này", HttpStatus.FORBIDDEN, "payment.order_not_owned"),
    ORDER_ALREADY_PAID(40003, "Đơn hàng đã được thanh toán", HttpStatus.CONFLICT, "payment.order_already_paid"),
    PAYMENT_LINK_FAILED(40004, "Không thể tạo link thanh toán", HttpStatus.INTERNAL_SERVER_ERROR, "payment.link_failed"),
    INVALID_ORDER_TYPE(40005, "Loại đơn hàng không hợp lệ", HttpStatus.BAD_REQUEST, "payment.invalid_order_type"),
    INVALID_WEBHOOK_SIGNATURE(40006, "Chữ ký webhook không hợp lệ", HttpStatus.UNAUTHORIZED, "payment.invalid_signature"),
    PAYMENT_NOT_FOUND(40007, "Không tìm thấy giao dịch thanh toán", HttpStatus.NOT_FOUND, "payment.not_found"),
    ORDER_NOT_PAYABLE(40008, "Đơn hàng không ở trạng thái có thể thanh toán", HttpStatus.BAD_REQUEST, "payment.order_not_payable");

    private final int code;
    private final String message;
    private final HttpStatus status;
    private final String errorKey;

    @Override
    public String getDomain() {
        return "PAYMENT";
    }

    public static PaymentErrorCode valueOf(int code) {
        for (PaymentErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        throw new IllegalArgumentException("No matching constant for [" + code + "]");
    }
}
