package com.fpt.printhub_3d.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Interface for domain-specific error codes.
 * Similar to Spring's HttpStatus enum pattern.
 *
 * Each domain should implement this interface with their own enum.
 * Example: UserErrorCode, OrderErrorCode, PaymentErrorCode
 */
public interface ErrorCode {

    /**
     * Return the integer value of this error code.
     */
    int getCode();

    /**
     * Return the reason phrase of this error code.
     */
    String getMessage();

    /**
     * Return the HTTP status associated with this error code.
     */
    HttpStatus getStatus();

    /**
     * Return the business error key for client identification/i18n.
     */
    String getErrorKey();

    /**
     * Return the domain/category of this error code.
     */
    default String getDomain() {
        return "COMMON";
    }

    /**
     * Check if this is a client error (4xx).
     */
    default boolean isClientError() {
        return getStatus().is4xxClientError();
    }

    /**
     * Check if this is a server error (5xx).
     */
    default boolean isServerError() {
        return getStatus().is5xxServerError();
    }

    /**
     * Check if this is a success response (2xx).
     */
    default boolean isSuccess() {
        return getStatus().is2xxSuccessful();
    }
}


