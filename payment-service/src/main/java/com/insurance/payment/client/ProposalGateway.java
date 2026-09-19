package com.insurance.payment.client;

import com.insurance.common.exception.ResourceNotFoundException;
import com.insurance.common.exception.ServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProposalGateway {

    static final String BACKEND = "proposal-service";

    private final ProposalClient client;

    @CircuitBreaker(name = BACKEND, fallbackMethod = "unavailable")
    @Retry(name = BACKEND)
    public ProposalClient.ProposalView get(String proposalNumber) {
        try {
            return client.get(proposalNumber);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Proposal " + proposalNumber + " not found");
        }
    }

    ProposalClient.ProposalView unavailable(String proposalNumber, Throwable cause) {
        if (cause instanceof ResourceNotFoundException nf) {
            throw nf;
        }
        log.error("{} call failed: {}", BACKEND, cause.toString());
        throw new ServiceUnavailableException(BACKEND);
    }
}
