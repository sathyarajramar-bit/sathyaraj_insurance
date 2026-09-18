package com.insurance.product.eligibility;

import com.insurance.product.entity.VehicleType;

import java.math.BigDecimal;

/** Framework-free input of the evaluator (the DTO is converted to this so the domain logic has no web types). */
public record RiskProfile(VehicleType vehicleType, int vehicleAgeYears, BigDecimal idv, int engineCapacityCc,
                          String fuelType, int driverAge) {
}
