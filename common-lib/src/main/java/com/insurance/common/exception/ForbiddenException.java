package com.insurance.common.exception;

import org.springframework.http.HttpStatus;

/** 403: the caller is authenticated but may not act on this resource (e.g. another customer's policy). */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
