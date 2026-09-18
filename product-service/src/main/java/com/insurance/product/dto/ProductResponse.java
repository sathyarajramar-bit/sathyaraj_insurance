package com.insurance.product.dto;

import com.insurance.product.entity.CoverageType;
import com.insurance.product.entity.ProductType;
import com.insurance.product.entity.VehicleType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ProductResponse(Long id, String code, String name, String description, ProductType productType,
                              VehicleType vehicleType, CoverageType coverageType, Integer termMonths, boolean active,
                              LocalDate effectiveFrom, LocalDate effectiveTo,
                              List<CoverageResponse> coverages, List<AddOnResponse> addOns,
                              List<EligibilityRuleResponse> eligibilityRules,
                              Instant createdAt, Instant updatedAt) {
}
