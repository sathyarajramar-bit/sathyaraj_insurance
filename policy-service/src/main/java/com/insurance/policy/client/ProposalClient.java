package com.insurance.policy.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

@FeignClient(name = "proposal-service", path = "/api/proposals")
public interface ProposalClient {

    @GetMapping("/{proposalNumber}")
    ProposalView get(@PathVariable("proposalNumber") String proposalNumber);

    record ProposalView(String proposalNumber, String status, Long userId, Long customerId, String quoteNumber, Long productId,
                        String productCode, String productName, String coverageType, Integer termMonths, Long vehicleId,
                        String registrationNumber, BigDecimal idv, BigDecimal premiumAmount, Proposer proposer, Nominee nominee) {
    }

    record Proposer(String firstName, String lastName, String email) {
    }

    record Nominee(String name, String relationship) {
    }
}
