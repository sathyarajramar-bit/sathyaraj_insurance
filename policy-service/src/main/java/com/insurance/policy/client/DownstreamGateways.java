package com.insurance.policy.client;

import com.insurance.common.exception.ResourceNotFoundException;
import com.insurance.common.exception.ServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Resilient reads of proposal and payment. Both are idempotent GETs: retry + circuit breaker + 503 fallback. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownstreamGateways {

    private final ProposalClient proposalClient;
    private final PaymentClient paymentClient;

    @CircuitBreaker(name = "proposal-service", fallbackMethod = "proposalUnavailable")
    @Retry(name = "proposal-service")
    public ProposalClient.ProposalView proposal(String proposalNumber) {
        try {
            return proposalClient.get(proposalNumber);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Proposal " + proposalNumber + " not found");
        }
    }

    @CircuitBreaker(name = "payment-service", fallbackMethod = "paymentUnavailable")
    @Retry(name = "payment-service")
    public PaymentClient.PaymentView payment(String reference) {
        try {
            return paymentClient.get(reference);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Payment " + reference + " not found");
        }
    }

    ProposalClient.ProposalView proposalUnavailable(String proposalNumber, Throwable cause) {
        throw unavailable("proposal-service", cause);
    }

    PaymentClient.PaymentView paymentUnavailable(String reference, Throwable cause) {
        throw unavailable("payment-service", cause);
    }

    private static RuntimeException unavailable(String backend, Throwable cause) {
        if (cause instanceof ResourceNotFoundException nf) {
            return nf;
        }
        log.error("{} call failed: {}", backend, cause.toString());
        return new ServiceUnavailableException(backend);
    }
}
