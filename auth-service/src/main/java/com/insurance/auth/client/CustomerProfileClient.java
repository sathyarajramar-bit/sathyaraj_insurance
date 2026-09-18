package com.insurance.auth.client;

import com.insurance.common.exception.BusinessException;
import com.insurance.common.exception.ServiceUnavailableException;
import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Anti-corruption layer around the Feign client: the rest of auth-service never sees Feign types.
 * Transport failures become {@link ServiceUnavailableException} (503); a 4xx from customer-service
 * means our request was rejected and is reported as a business error.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerProfileClient {

    private final CustomerClient customerClient;

    public CustomerResponse createProfile(CreateCustomerRequest request) {
        try {
            return customerClient.create(request);
        } catch (RetryableException e) {
            log.error("customer-service unreachable while creating profile for user {}", request.userId(), e);
            throw new ServiceUnavailableException("customer-service");
        } catch (FeignException e) {
            if (e.status() >= 500 || e.status() < 0) {
                log.error("customer-service failed ({}) while creating profile for user {}", e.status(), request.userId(), e);
                throw new ServiceUnavailableException("customer-service");
            }
            log.warn("customer-service rejected profile creation for user {}: {} {}", request.userId(), e.status(), e.contentUTF8());
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CUSTOMER_PROFILE_REJECTED",
                    "Customer profile could not be created");
        }
    }
}
