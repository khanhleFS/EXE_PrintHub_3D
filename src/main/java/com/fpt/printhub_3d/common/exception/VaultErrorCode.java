package com.fpt.printhub_3d.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VaultErrorCode implements ErrorCode {

    FILE_EMPTY(26001, "Tệp tải lên không được để trống", HttpStatus.BAD_REQUEST, "vault.file_empty"),
    FILE_TOO_LARGE(26002, "Dung lượng tệp vượt quá giới hạn 20MB", HttpStatus.BAD_REQUEST, "vault.file_too_large"),
    INVALID_FILE_TYPE(26003, "Chỉ chấp nhận các tệp định dạng STL, OBJ, STEP (.stl, .obj, .step, .stp)", HttpStatus.BAD_REQUEST, "vault.invalid_file_type"),
    FILE_NOT_FOUND(26004, "Không tìm thấy tệp thiết kế", HttpStatus.NOT_FOUND, "vault.file_not_found"),
    FILE_IN_USE(26005, "Tệp đang được sử dụng trong yêu cầu in, không thể xóa", HttpStatus.CONFLICT, "vault.file_in_use"),
    FORBIDDEN_FILE_ACCESS(26006, "Bạn không có quyền truy cập hoặc thao tác trên tệp này", HttpStatus.FORBIDDEN, "vault.forbidden_access"),
    FILE_STORAGE_ERROR(26007, "Lỗi trong quá trình lưu trữ hoặc đọc tệp", HttpStatus.INTERNAL_SERVER_ERROR, "vault.storage_error");

    private final int code;
    private final String message;
    private final HttpStatus status;
    private final String errorKey;

    @Override
    public String getDomain() {
        return "VAULT";
    }
}
