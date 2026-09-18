package com.insurance.product.eligibility;

import com.insurance.product.entity.EligibilityRule;
import com.insurance.product.entity.Product;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Evaluates a product's eligibility rules against a risk profile. Pure domain logic: no Spring, no JPA
 * calls, so it is unit-tested exhaustively without a context. All violations are collected (not just the
 * first) so the customer sees everything that disqualifies them at once.
 */
@Component
public class EligibilityEvaluator {

    public EligibilityResult evaluate(Product product, RiskProfile profile, LocalDate today) {
        List<String> violations = new ArrayList<>();
        if (!product.isSellableOn(today)) {
            violations.add("Product " + product.getCode() + " is not available for sale");
        }
        if (product.getVehicleType() != null && product.getVehicleType() != profile.vehicleType()) {
            violations.add("Product " + product.getCode() + " covers " + product.getVehicleType()
                    + " vehicles only, not " + profile.vehicleType());
        }
        for (EligibilityRule rule : product.getEligibilityRules()) {
            if (!satisfies(rule, profile)) {
                violations.add(rule.getMessage());
            }
        }
        return EligibilityResult.of(violations);
    }

    boolean satisfies(EligibilityRule rule, RiskProfile p) {
        String value = rule.getRuleValue().trim();
        return switch (rule.getRuleType()) {
            case MAX_VEHICLE_AGE_YEARS -> p.vehicleAgeYears() <= Integer.parseInt(value);
            case MIN_VEHICLE_AGE_YEARS -> p.vehicleAgeYears() >= Integer.parseInt(value);
            case MIN_DRIVER_AGE -> p.driverAge() >= Integer.parseInt(value);
            case MAX_DRIVER_AGE -> p.driverAge() <= Integer.parseInt(value);
            case MIN_IDV -> p.idv().compareTo(new BigDecimal(value)) >= 0;
            case MAX_IDV -> p.idv().compareTo(new BigDecimal(value)) <= 0;
            case MIN_ENGINE_CC -> p.engineCapacityCc() >= Integer.parseInt(value);
            case MAX_ENGINE_CC -> p.engineCapacityCc() <= Integer.parseInt(value);
            case ALLOWED_FUEL_TYPES -> Arrays.stream(value.split(","))
                    .map(String::trim)
                    .anyMatch(fuel -> fuel.equalsIgnoreCase(p.fuelType()));
        };
    }
}
