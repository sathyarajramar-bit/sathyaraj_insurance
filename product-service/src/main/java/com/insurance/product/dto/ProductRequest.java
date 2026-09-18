package com.insurance.product.dto;

import com.insurance.product.entity.CoverageType;
import com.insurance.product.entity.ProductType;
import com.insurance.product.entity.VehicleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** The whole aggregate in one payload: PUT replaces coverages, add-ons, rules and pricing atomically. */
public record ProductRequest(
        @NotBlank(message = "Product code is required")
        @Pattern(regexp = "^[A-Z0-9-]{3,40}$", message = "Product code must be upper case letters, digits or hyphens") String code,
        @NotBlank(message = "Product name is required") @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @NotNull(message = "Product type is required") ProductType productType,
        VehicleType vehicleType,
        @NotNull(message = "Coverage type is required") CoverageType coverageType,
        @NotNull(message = "Term is required") @Min(1) @Max(60) Integer termMonths,
        Boolean active,
        @NotNull(message = "Effective from date is required") LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @NotEmpty(message = "At least one coverage is required") @Valid List<CoverageRequest> coverages,
        @Valid List<AddOnRequest> addOns,
        @Valid List<EligibilityRuleRequest> eligibilityRules,
        @NotNull(message = "Pricing configuration is required") @Valid PricingRequest pricing) {
}
