package com.insurance.quote.exception;

import com.insurance.common.exception.ResourceNotFoundException;

public class QuoteNotFoundException extends ResourceNotFoundException {

    public QuoteNotFoundException(String quoteNumber) {
        super("Quote " + quoteNumber + " not found");
    }
}
