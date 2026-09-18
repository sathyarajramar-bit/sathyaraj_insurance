package com.insurance.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record PricingRequest(
        @NotNull @PositiveOrZero @Max(100) BigDecimal baseRatePercentOfIdv,
        @NotNull @PositiveOrZero BigDecimal minBasePremium,
        @NotNull @PositiveOrZero BigDecimal thirdPartyPremium,
        @NotNull @PositiveOrZero @Max(100) BigDecimal vehicleAgeLoadingPercentPerYear,
        @NotNull @PositiveOrZero @Max(100) BigDecimal maxVehicleAgeLoadingPercent,
        @NotNull @Min(16) @Max(99) Integer youngDriverAgeLimit,
        @NotNull @PositiveOrZero @Max(100) BigDecimal youngDriverLoadingPercent,
        @NotNull @PositiveOrZero @Max(100) BigDecimal maxNcbDiscountPercent,
        @NotNull @PositiveOrZero @Max(100) BigDecimal electricVehicleDiscountPercent,
        @NotNull @PositiveOrZero @Max(100) BigDecimal taxPercent) {
}
