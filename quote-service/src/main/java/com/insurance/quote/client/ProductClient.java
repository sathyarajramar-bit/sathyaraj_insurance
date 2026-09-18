package com.insurance.quote.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contract with product-service (owned by product-service; only the fields quote-service needs are declared,
 * unknown JSON fields are ignored). Resolved through Eureka; the service JWT is added by common-lib.
 */
@FeignClient(name = "product-service", path = "/api/products")
public interface ProductClient {

    @GetMapping("/{id}")
    ProductDetails getProduct(@PathVariable("id") Long id);

    @GetMapping("/{id}/pricing")
    ProductPricing getPricing(@PathVariable("id") Long id);

    @PostMapping("/{id}/eligibility-check")
    EligibilityResult checkEligibility(@PathVariable("id") Long id, @RequestBody RiskProfile profile);

    record ProductDetails(Long id, String code, String name, String productType, String vehicleType, String coverageType,
                          Integer termMonths, boolean active, List<ProductAddOn> addOns) {
    }

    record ProductAddOn(Long id, String code, String name, String pricingType, BigDecimal rate, boolean active) {
    }

    record ProductPricing(Long productId, String productCode, BigDecimal baseRatePercentOfIdv, BigDecimal minBasePremium,
                          BigDecimal thirdPartyPremium, BigDecimal vehicleAgeLoadingPercentPerYear,
                          BigDecimal maxVehicleAgeLoadingPercent, Integer youngDriverAgeLimit,
                          BigDecimal youngDriverLoadingPercent, BigDecimal maxNcbDiscountPercent,
                          BigDecimal electricVehicleDiscountPercent, BigDecimal taxPercent) {
    }

    record RiskProfile(String vehicleType, Integer vehicleAgeYears, BigDecimal idv, Integer engineCapacityCc,
                       String fuelType, Integer driverAge) {
    }

    record EligibilityResult(Long productId, String productCode, boolean eligible, List<String> violations) {
    }
}
