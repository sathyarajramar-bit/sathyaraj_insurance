package com.insurance.payment.dto;

import com.insurance.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Exactly one of proposalNumber (new policy) or policyNumber (renewal) must be given. The amount is never
 * sent by the client: it is read from the proposal/policy, so a tampered request cannot underpay.
 *
 * @param instrument masked card / UPI id; the mock provider declines instruments ending in 0002
 */
public record PaymentRequest(
        @Size(max = 30) String proposalNumber,
        @Size(max = 30) String policyNumber,
        @NotNull(message = "paymentMethod is required") PaymentMethod paymentMethod,
        @Pattern(regexp = "^[A-Za-z0-9@._*-]{4,40}$", message = "instrument is invalid") String instrument) {
}
