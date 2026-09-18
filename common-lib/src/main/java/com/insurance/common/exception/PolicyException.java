package com.insurance.common.exception;

import org.springframework.http.HttpStatus;

/** 422: policy lifecycle rule violated (renewal window passed, policy not active, ...). */
public class PolicyException extends BusinessException {

    public PolicyException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "POLICY_ERROR", message);
    }
}
