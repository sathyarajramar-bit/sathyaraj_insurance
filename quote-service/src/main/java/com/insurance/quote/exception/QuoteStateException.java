package com.insurance.quote.exception;

import com.insurance.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 422: the requested transition is not allowed from the quote's current status. */
public class QuoteStateException extends BusinessException {

    public QuoteStateException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "QUOTE_INVALID_STATE", message);
    }
}
