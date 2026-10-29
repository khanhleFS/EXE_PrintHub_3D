package com.fpt.printhub_3d.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CustomPrintErrorCode implements ErrorCode {

    INVALID_FILE(21003, "File đính kèm không hợp lệ hoặc trống", HttpStatus.BAD_REQUEST, "custom_print.invalid_file"),
    INVALID_FILE_EXTENSION(21004, "Chỉ chấp nhận tệp thiết kế hình học định dạng .STL", HttpStatus.BAD_REQUEST, "custom_print.invalid_file_extension"),
    FILE_STORAGE_FAILED(21005, "Không thể lưu trữ tệp thiết kế", HttpStatus.INTERNAL_SERVER_ERROR, "custom_print.file_storage_failed"),
    PAYMENT_REQUIRED(21006, "Yêu cầu in 3D chưa đủ điều kiện thanh toán để tiến hành sản xuất", HttpStatus.CONFLICT, "custom_print.payment_required"),
    INVALID_ORDER_TRANSITION(21007, "Chuyển trạng thái yêu cầu in không hợp lệ", HttpStatus.BAD_REQUEST, "custom_print.invalid_transition");

    private final int code;
    private final String message;
    private final HttpStatus status;
    private final String errorKey;

    @Override
    public String getDomain() {
        return "CUSTOM_PRINT";
    }
}
