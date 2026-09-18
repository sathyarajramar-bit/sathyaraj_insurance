package com.insurance.product.dto;

import java.math.BigDecimal;

/** Read by quote-service; not part of the public product view. */
public record PricingResponse(Long productId, String productCode, BigDecimal baseRatePercentOfIdv,
                              BigDecimal minBasePremium, BigDecimal thirdPartyPremium,
                              BigDecimal vehicleAgeLoadingPercentPerYear, BigDecimal maxVehicleAgeLoadingPercent,
                              Integer youngDriverAgeLimit, BigDecimal youngDriverLoadingPercent,
                              BigDecimal maxNcbDiscountPercent, BigDecimal electricVehicleDiscountPercent,
                              BigDecimal taxPercent) {
}
