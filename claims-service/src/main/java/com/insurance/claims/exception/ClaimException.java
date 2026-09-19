package com.insurance.claims.exception;

import com.insurance.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 422: a claims business rule or state transition was violated. */
public class ClaimException extends BusinessException {

    public ClaimException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "CLAIM_ERROR", message);
    }
}
