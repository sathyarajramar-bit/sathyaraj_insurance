package com.insurance.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for every expected, domain-level failure.
 *
 * <p>Each subclass fixes an HTTP status and a stable machine-readable {@code errorCode}
 * (e.g. {@code QUOTE_EXPIRED}) so clients can branch on the code instead of parsing messages.
 * Unexpected failures (bugs, infrastructure) are NOT business exceptions and surface as 500.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public BusinessException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public BusinessException(String message) {
        this(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", message);
    }
}
