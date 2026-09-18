package com.insurance.proposal.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.time.Instant;

/** Contract with quote-service: the accepted quote a proposal is built from (SERVICE role may read any quote). */
@FeignClient(name = "quote-service", path = "/api/quotes")
public interface QuoteClient {

    @GetMapping("/{quoteNumber}")
    QuoteView getQuote(@PathVariable("quoteNumber") String quoteNumber);

    record QuoteView(Long id, String quoteNumber, String status, Long userId, Long customerId, Long productId,
                     String productCode, String productName, String coverageType, Integer termMonths, Long vehicleId,
                     String registrationNumber, BigDecimal idv, BigDecimal finalPremium, Instant validUntil) {
    }
}
