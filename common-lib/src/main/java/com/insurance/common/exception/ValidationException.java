package com.insurance.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 400: input that passes Bean Validation but fails a business validation rule
 * (e.g. "quote cannot be generated for an expired vehicle registration").
 */
public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
