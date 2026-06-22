package com.fpt.printhub_3d.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CustomPrintErrorCode implements ErrorCode {

    MAKER_NOT_FOUND(21001, "Maker được yêu cầu không tồn tại hoặc đã bị xóa", HttpStatus.NOT_FOUND, "custom_print.maker_not_found"),
    MAKER_INVALID_ROLE(21002, "Người nhận yêu cầu in không phải là Maker", HttpStatus.BAD_REQUEST, "custom_print.maker_invalid_role"),
    INVALID_FILE(21003, "File đính kèm không hợp lệ hoặc trống", HttpStatus.BAD_REQUEST, "custom_print.invalid_file"),
    INVALID_FILE_EXTENSION(21004, "Chỉ chấp nhận tệp thiết kế hình học định dạng .STL", HttpStatus.BAD_REQUEST, "custom_print.invalid_file_extension"),
    FILE_STORAGE_FAILED(21005, "Không thể lưu trữ tệp thiết kế", HttpStatus.INTERNAL_SERVER_ERROR, "custom_print.file_storage_failed");

    private final int code;
    private final String message;
    private final HttpStatus status;
    private final String errorKey;

    @Override
    public String getDomain() {
        return "CUSTOM_PRINT";
    }
}
