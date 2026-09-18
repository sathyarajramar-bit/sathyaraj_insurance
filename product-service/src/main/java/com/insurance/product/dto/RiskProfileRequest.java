package com.insurance.product.dto;

import com.insurance.product.entity.VehicleType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** The facts eligibility rules are evaluated against (supplied by quote-service from customer + vehicle data). */
public record RiskProfileRequest(
        @NotNull(message = "Vehicle type is required") VehicleType vehicleType,
        @NotNull(message = "Vehicle age is required") @Min(0) Integer vehicleAgeYears,
        @NotNull(message = "IDV is required") @Positive BigDecimal idv,
        @NotNull(message = "Engine capacity is required") @Positive Integer engineCapacityCc,
        @NotBlank(message = "Fuel type is required") String fuelType,
        @NotNull(message = "Driver age is required") @Min(16) Integer driverAge) {
}
