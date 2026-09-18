package com.insurance.common.exception;

import org.springframework.http.HttpStatus;

/** 409: a unique business key (email, vehicle registration number, policy number) already exists. */
public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", message);
    }
}
