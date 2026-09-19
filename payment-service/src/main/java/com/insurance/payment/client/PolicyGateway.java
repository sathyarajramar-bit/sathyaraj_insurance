package com.insurance.payment.client;

import com.insurance.common.exception.ResourceNotFoundException;
import com.insurance.common.exception.ServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Reads are retried; issue() is not retried here because the outbox dispatcher owns retries with backoff. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyGateway {

    static final String BACKEND = "policy-service";

    private final PolicyClient client;

    @CircuitBreaker(name = BACKEND, fallbackMethod = "unavailable")
    @Retry(name = BACKEND)
    public PolicyClient.PolicyView get(String policyNumber) {
        try {
            return client.get(policyNumber);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Policy " + policyNumber + " not found");
        }
    }

    @CircuitBreaker(name = BACKEND)
    public PolicyClient.PolicyView issue(PaymentSuccessfulEvent event) {
        return client.issue(event);
    }

    PolicyClient.PolicyView unavailable(String policyNumber, Throwable cause) {
        if (cause instanceof ResourceNotFoundException nf) {
            throw nf;
        }
        log.error("{} call failed: {}", BACKEND, cause.toString());
        throw new ServiceUnavailableException(BACKEND);
    }
}
