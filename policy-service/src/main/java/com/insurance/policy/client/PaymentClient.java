package com.insurance.policy.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

/** Used to VERIFY that a payment referenced by an issue request is really SUCCESS (never trust the event alone). */
@FeignClient(name = "payment-service", path = "/api/payments")
public interface PaymentClient {

    @GetMapping("/{reference}")
    PaymentView get(@PathVariable("reference") String reference);

    record PaymentView(String paymentReference, String status, String purpose, String proposalNumber, String policyNumber,
                       Long userId, Long customerId, BigDecimal amount) {
    }
}
