package com.insurance.notification.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Resolves the recipient's contact details when the producer only knew ids. */
@FeignClient(name = "customer-service", path = "/api/customers")
public interface CustomerClient {

    @GetMapping("/{customerId}")
    CustomerContact getById(@PathVariable("customerId") Long customerId);

    @GetMapping("/by-user/{userId}")
    CustomerContact getByUserId(@PathVariable("userId") Long userId);

    record CustomerContact(Long id, Long userId, String email, String phone, String firstName) {
    }
}
