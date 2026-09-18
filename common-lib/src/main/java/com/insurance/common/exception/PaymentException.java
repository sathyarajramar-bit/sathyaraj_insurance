package com.insurance.common.exception;

import org.springframework.http.HttpStatus;

/** 422: payment could not be processed (declined, already processed, wrong amount). */
public class PaymentException extends BusinessException {

    public PaymentException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_ERROR", message);
    }
}
