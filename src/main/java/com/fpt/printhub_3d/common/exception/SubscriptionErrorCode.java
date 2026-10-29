package com.fpt.printhub_3d.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SubscriptionErrorCode implements ErrorCode {

    PLAN_NOT_FOUND(40001, "Gói dịch vụ không tồn tại", HttpStatus.NOT_FOUND, "subscription.plan_not_found"),
    DUPLICATE_PLAN_NAME(40002, "Tên gói dịch vụ đã tồn tại", HttpStatus.CONFLICT, "subscription.duplicate_plan_name"),
    ACTIVE_PLAN_CONFLICT(40003, "Người dùng đang có gói hội viên khác còn hiệu lực", HttpStatus.CONFLICT, "subscription.active_plan_conflict"),
    PLAN_INACTIVE(40004, "Gói dịch vụ hiện tại không hoạt động", HttpStatus.BAD_REQUEST, "subscription.plan_inactive"),
    POINT_REDEMPTION_NOT_SUPPORTED(40005, "Gói dịch vụ không hỗ trợ đổi bằng điểm thưởng", HttpStatus.BAD_REQUEST, "subscription.point_redemption_not_supported"),
    INSUFFICIENT_POINTS(40006, "Số dư điểm thưởng không đủ", HttpStatus.CONFLICT, "subscription.insufficient_points"),
    INVALID_RECIPIENT(40007, "Tài khoản người nhận không hợp lệ", HttpStatus.BAD_REQUEST, "subscription.invalid_recipient"),
    IDEMPOTENCY_CONFLICT(40008, "Mã yêu cầu (requestId) đã được sử dụng với nội dung khác", HttpStatus.CONFLICT, "subscription.idempotency_conflict"),
    MONEY_PURCHASE_NOT_SUPPORTED(40009, "Gói dịch vụ không hỗ trợ mua bằng tiền", HttpStatus.BAD_REQUEST, "subscription.money_purchase_not_supported"),
    PURCHASE_NOT_FOUND(40010, "Không tìm thấy giao dịch mua gói hội viên", HttpStatus.NOT_FOUND, "subscription.purchase_not_found"),
    PURCHASE_NOT_PAYABLE(40011, "Giao dịch mua gói không ở trạng thái có thể thanh toán", HttpStatus.BAD_REQUEST, "subscription.purchase_not_payable");

    private final int code;
    private final String message;
    private final HttpStatus status;
    private final String errorKey;

    @Override
    public String getDomain() {
        return "SUBSCRIPTION";
    }
}
