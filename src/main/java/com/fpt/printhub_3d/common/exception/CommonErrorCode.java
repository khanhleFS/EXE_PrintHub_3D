package com.fpt.printhub_3d.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Common error codes used across all domains.
 *
 * Error Code Convention:
 * - 1xxx: Success codes
 * - 2xxx: Common client errors
 * - 3xxx: Common server errors
 *
 * For domain-specific errors, create separate enums:
 * - UserErrorCode (10xxx)
 * - AuthErrorCode (11xxx)
 * - OrderErrorCode (20xxx)
 * etc.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    // 1xxx - Success
    SUCCESS(1000, "Success", HttpStatus.OK, "common.success"),
    CREATED(1001, "Resource created successfully", HttpStatus.CREATED, "common.created"),
    ACCEPTED(1002, "Request accepted", HttpStatus.ACCEPTED, "common.accepted"),

    // 2xxx - Common Client Errors
    BAD_REQUEST(2000, "Bad request", HttpStatus.BAD_REQUEST, "common.bad_request"),
    INVALID_INPUT(2001, "Invalid input provided", HttpStatus.BAD_REQUEST, "common.invalid_input"),
    VALIDATION_FAILED(2002, "Validation failed", HttpStatus.BAD_REQUEST, "common.validation_failed"),
    RESOURCE_NOT_FOUND(2003, "Resource not found", HttpStatus.NOT_FOUND, "common.resource_not_found"),
    UNAUTHORIZED(2004, "Unauthorized access", HttpStatus.UNAUTHORIZED, "common.unauthorized"),
    FORBIDDEN(2005, "Access forbidden", HttpStatus.FORBIDDEN, "common.forbidden"),
    CONFLICT(2006, "Resource conflict", HttpStatus.CONFLICT, "common.conflict"),

    // 3xxx - Common Server Errors
    INTERNAL_ERROR(3000, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR, "common.internal_error"),
    SERVICE_UNAVAILABLE(3001, "Service temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE,
            "common.service_unavailable"),
    DATABASE_ERROR(3002, "Database operation failed", HttpStatus.INTERNAL_SERVER_ERROR, "common.database_error"),
    EXTERNAL_SERVICE_ERROR(3003, "External service error", HttpStatus.BAD_GATEWAY, "common.external_service_error"),

    UNEXPECTED_ERROR(5000, "Unexpected error", HttpStatus.INTERNAL_SERVER_ERROR, "common.unexpected_error");

    private final int code;
    private final String message;
    private final HttpStatus status;
    private final String errorKey;

    @Override
    public String getDomain() {
        return "COMMON";
    }

    /**
     * Find ErrorCode by code value.
     * Similar to HttpStatus.valueOf(int statusCode)
     */
    public static CommonErrorCode valueOf(int code) {
        for (CommonErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        throw new IllegalArgumentException("No matching constant for [" + code + "]");
    }
}


