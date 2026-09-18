package com.insurance.proposal.client;

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
public class CustomerGateway {

    static final String BACKEND = "customer-service";

    private final CustomerClient customerClient;

    @CircuitBreaker(name = BACKEND, fallbackMethod = "customerUnavailable")
    @Retry(name = BACKEND)
    public CustomerClient.CustomerProfile getById(Long customerId) {
        try {
            return customerClient.getById(customerId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Customer", customerId);
        }
    }

    CustomerClient.CustomerProfile customerUnavailable(Long customerId, Throwable cause) {
        if (cause instanceof ResourceNotFoundException notFound) {
            throw notFound;
        }
        log.error("{} call failed: {}", BACKEND, cause.toString());
        throw new ServiceUnavailableException(BACKEND);
    }
}
