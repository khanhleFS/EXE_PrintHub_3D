package com.fpt.printhub_3d.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewDisputeErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(50001, "Đơn hàng không tồn tại", HttpStatus.NOT_FOUND, "review_dispute.order_not_found"),
    ORDER_NOT_COMPLETED(50002, "Đơn hàng chưa hoàn tất", HttpStatus.BAD_REQUEST, "review.order_not_completed"),
    REVIEW_ALREADY_EXISTS(50003, "Đơn hàng này đã được đánh giá", HttpStatus.CONFLICT, "review.already_exists"),
    NOT_ORDER_BUYER(50004, "Chỉ khách hàng của đơn hàng mới được thực hiện thao tác này", HttpStatus.FORBIDDEN, "review.not_order_buyer"),
    DISPUTE_ALREADY_EXISTS(50005, "Đơn hàng này đã có hồ sơ khiếu nại", HttpStatus.CONFLICT, "dispute.already_exists"),
    NOT_ORDER_PARTICIPANT(50006, "Chỉ Customer hoặc Maker của đơn hàng mới được mở khiếu nại", HttpStatus.FORBIDDEN, "dispute.not_order_participant"),
    DISPUTE_NOT_FOUND(50007, "Hồ sơ khiếu nại không tồn tại", HttpStatus.NOT_FOUND, "dispute.not_found"),
    INVALID_RESOLUTION_STATUS(50008, "Trạng thái phán quyết không hợp lệ", HttpStatus.BAD_REQUEST, "dispute.invalid_resolution_status"),
    INVALID_REFUND(50009, "Thông tin hoàn tiền không hợp lệ", HttpStatus.BAD_REQUEST, "dispute.invalid_refund");

    private final int code;
    private final String message;
    private final HttpStatus status;
    private final String errorKey;

    @Override
    public String getDomain() {
        return "REVIEW_DISPUTE";
    }
}
