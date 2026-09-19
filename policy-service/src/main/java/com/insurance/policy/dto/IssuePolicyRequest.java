package com.insurance.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/** Mirror of payment-service's PaymentSuccessfulEvent (the contract is owned by payment-service). */
public record IssuePolicyRequest(
        @NotBlank(message = "paymentReference is required") String paymentReference,
        @NotBlank(message = "purpose is required") String purpose,
        String proposalNumber,
        String policyNumber,
        @NotNull Long userId,
        @NotNull Long customerId,
        BigDecimal amount,
        String currency,
        Instant paidAt) {
}
