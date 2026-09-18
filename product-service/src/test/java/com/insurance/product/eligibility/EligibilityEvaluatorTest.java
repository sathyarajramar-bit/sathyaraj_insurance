package com.insurance.product.eligibility;

import com.insurance.product.entity.CoverageType;
import com.insurance.product.entity.EligibilityRule;
import com.insurance.product.entity.Product;
import com.insurance.product.entity.ProductType;
import com.insurance.product.entity.RuleType;
import com.insurance.product.entity.VehicleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityEvaluatorTest {

    private final EligibilityEvaluator evaluator = new EligibilityEvaluator();
    private final LocalDate today = LocalDate.of(2026, 9, 18);

    private Product carProduct(boolean active, EligibilityRule... rules) {
        Product product = Product.builder().code("MOTOR-CAR-COMP").name("Car").productType(ProductType.MOTOR)
                .vehicleType(VehicleType.CAR).coverageType(CoverageType.COMPREHENSIVE).termMonths(12)
                .active(active).effectiveFrom(LocalDate.of(2026, 1, 1)).build();
        product.replaceEligibilityRules(List.of(rules));
        return product;
    }

    private static EligibilityRule rule(RuleType type, String value, String message) {
        return EligibilityRule.builder().ruleType(type).ruleValue(value).message(message).build();
    }

    private static RiskProfile profile(VehicleType type, int age, String idv, int cc, String fuel, int driverAge) {
        return new RiskProfile(type, age, new BigDecimal(idv), cc, fuel, driverAge);
    }

    @Test
    void eligibleWhenAllRulesPass() {
        Product product = carProduct(true,
                rule(RuleType.MAX_VEHICLE_AGE_YEARS, "15", "too old"),
                rule(RuleType.MIN_IDV, "50000", "idv too low"),
                rule(RuleType.ALLOWED_FUEL_TYPES, "PETROL, DIESEL", "fuel not covered"));

        EligibilityResult result = evaluator.evaluate(product, profile(VehicleType.CAR, 3, "650000", 1498, "petrol", 30), today);

        assertThat(result.eligible()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void collectsEveryViolationNotJustTheFirst() {
        Product product = carProduct(true,
                rule(RuleType.MAX_VEHICLE_AGE_YEARS, "15", "too old"),
                rule(RuleType.MIN_DRIVER_AGE, "18", "driver too young"),
                rule(RuleType.MAX_ENGINE_CC, "2000", "engine too big"));

        EligibilityResult result = evaluator.evaluate(product, profile(VehicleType.CAR, 20, "650000", 2500, "PETROL", 17), today);

        assertThat(result.eligible()).isFalse();
        assertThat(result.violations()).containsExactly("too old", "driver too young", "engine too big");
    }

    @Test
    void vehicleTypeMismatchIsAViolation() {
        EligibilityResult result = evaluator.evaluate(carProduct(true), profile(VehicleType.BIKE, 1, "90000", 150, "PETROL", 30), today);

        assertThat(result.eligible()).isFalse();
        assertThat(result.violations().get(0)).contains("covers CAR vehicles only");
    }

    @Test
    void inactiveOrNotYetEffectiveProductIsNotEligible() {
        assertThat(evaluator.evaluate(carProduct(false), profile(VehicleType.CAR, 1, "90000", 1000, "PETROL", 30), today).eligible()).isFalse();
        assertThat(evaluator.evaluate(carProduct(true), profile(VehicleType.CAR, 1, "90000", 1000, "PETROL", 30), LocalDate.of(2025, 12, 31)).eligible()).isFalse();
    }

    @Test
    void fuelTypeComparisonIgnoresCaseAndSpaces() {
        EligibilityRule rule = rule(RuleType.ALLOWED_FUEL_TYPES, "PETROL , DIESEL", "x");
        assertThat(evaluator.satisfies(rule, profile(VehicleType.CAR, 1, "1", 1, "Diesel", 30))).isTrue();
        assertThat(evaluator.satisfies(rule, profile(VehicleType.CAR, 1, "1", 1, "CNG", 30))).isFalse();
    }
}
