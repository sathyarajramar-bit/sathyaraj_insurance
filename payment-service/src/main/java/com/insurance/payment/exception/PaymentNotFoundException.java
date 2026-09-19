package com.insurance.payment.exception;

import com.insurance.common.exception.ResourceNotFoundException;

public class PaymentNotFoundException extends ResourceNotFoundException {

    public PaymentNotFoundException(String reference) {
        super("Payment " + reference + " not found");
    }
}
