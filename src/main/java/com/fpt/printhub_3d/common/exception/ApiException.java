package com.fpt.printhub_3d.common.exception;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;
    private final transient Object result;
    private final boolean useHttpStatusCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, false);
    }

    public ApiException(ErrorCode errorCode, Object result) {
        this(errorCode, errorCode.getMessage(), result, true);
    }

    private ApiException(ErrorCode errorCode, String message, Object result, boolean useHttpStatusCode) {
        super(message);
        this.errorCode = errorCode;
        this.result = result;
        this.useHttpStatusCode = useHttpStatusCode;
    }
}
