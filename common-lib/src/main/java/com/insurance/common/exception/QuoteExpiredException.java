package com.insurance.common.exception;

import org.springframework.http.HttpStatus;

/** 422: the quote's validity window has passed; a new quote is required. */
public class QuoteExpiredException extends BusinessException {

    public QuoteExpiredException(String quoteNumber) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "QUOTE_EXPIRED", "Quote " + quoteNumber + " has expired");
    }
}
