package com.insurance.quote.client;

import com.insurance.common.exception.ResourceNotFoundException;
import com.insurance.common.exception.ServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Resilient facade over {@link CustomerClient}; same policy as {@link ProductCatalogGateway}. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerGateway {

    static final String BACKEND = "customer-service";

    private final CustomerClient customerClient;

    @CircuitBreaker(name = BACKEND, fallbackMethod = "profileUnavailable")
    @Retry(name = BACKEND)
    public CustomerClient.CustomerProfile getByUserId(Long userId) {
        try {
            return customerClient.getByUserId(userId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Customer profile not found for user " + userId);
        }
    }

    @CircuitBreaker(name = BACKEND, fallbackMethod = "profileUnavailable")
    @Retry(name = BACKEND)
    public CustomerClient.CustomerProfile getById(Long customerId) {
        try {
            return customerClient.getById(customerId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Customer", customerId);
        }
    }

    @CircuitBreaker(name = BACKEND, fallbackMethod = "vehicleUnavailable")
    @Retry(name = BACKEND)
    public CustomerClient.Vehicle getVehicle(Long customerId, Long vehicleId) {
        try {
            return customerClient.getVehicle(customerId, vehicleId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Vehicle " + vehicleId + " does not belong to customer " + customerId);
        }
    }

    CustomerClient.CustomerProfile profileUnavailable(Long id, Throwable cause) {
        throw unavailable(cause);
    }

    CustomerClient.Vehicle vehicleUnavailable(Long customerId, Long vehicleId, Throwable cause) {
        throw unavailable(cause);
    }

    private static RuntimeException unavailable(Throwable cause) {
        if (cause instanceof ResourceNotFoundException notFound) {
            return notFound;
        }
        log.error("{} call failed: {}", BACKEND, cause.toString());
        return new ServiceUnavailableException(BACKEND);
    }
}
