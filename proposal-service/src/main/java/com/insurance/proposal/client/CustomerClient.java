package com.insurance.proposal.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;

/** Contract with customer-service: profile to prefill the proposer and the KYC status checked at submission. */
@FeignClient(name = "customer-service", path = "/api/customers")
public interface CustomerClient {

    @GetMapping("/{customerId}")
    CustomerProfile getById(@PathVariable("customerId") Long customerId);

    record CustomerProfile(Long id, Long userId, String email, String firstName, String lastName, String phone,
                           LocalDate dateOfBirth, String kycStatus, Address address) {
    }

    record Address(String line1, String line2, String city, String state, String postalCode, String country) {
    }
}
