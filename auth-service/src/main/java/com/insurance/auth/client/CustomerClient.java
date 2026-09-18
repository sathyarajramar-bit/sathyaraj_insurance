package com.insurance.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Declarative HTTP client for customer-service.
 *
 * <p>Interview notes:
 * <ul>
 *   <li>{@code name} is the Eureka service id: Feign asks Spring Cloud LoadBalancer for an instance,
 *       so no URL is configured anywhere.</li>
 *   <li>Feign generates the implementation from the interface; common-lib's global RequestInterceptor adds
 *       the service JWT and the correlation id to every outbound request.</li>
 *   <li>Failures surface as {@code FeignException}; {@link CustomerProfileClient} translates them.
 *       Phase 5 wraps this in a Resilience4j circuit breaker with retries.</li>
 * </ul>
 */
@FeignClient(name = "customer-service", path = "/api/customers")
public interface CustomerClient {

    @PostMapping
    CustomerResponse create(@RequestBody CreateCustomerRequest request);
}
