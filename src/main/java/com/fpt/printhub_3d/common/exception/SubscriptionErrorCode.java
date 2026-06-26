package com.fpt.printhub_3d.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SubscriptionErrorCode implements ErrorCode {

    PLAN_NOT_FOUND(40001, "Gói dịch vụ không tồn tại", HttpStatus.NOT_FOUND, "subscription.plan_not_found"),
    DUPLICATE_PLAN_NAME(40002, "Tên gói dịch vụ đã tồn tại", HttpStatus.CONFLICT, "subscription.duplicate_plan_name");

    private final int code;
    private final String message;
    private final HttpStatus status;
    private final String errorKey;

    @Override
    public String getDomain() {
        return "SUBSCRIPTION";
    }
}
