package com.insurance.product.entity;

/**
 * Eligibility rule vocabulary. Numeric rules compare against a single number in {@code rule_value};
 * {@link #ALLOWED_FUEL_TYPES} holds a comma separated list. New product types (HEALTH, TRAVEL) add
 * their own constants here (e.g. MAX_TRIP_DAYS) without changing the table.
 */
public enum RuleType {
    MAX_VEHICLE_AGE_YEARS, MIN_VEHICLE_AGE_YEARS,
    MIN_DRIVER_AGE, MAX_DRIVER_AGE,
    MIN_IDV, MAX_IDV,
    MIN_ENGINE_CC, MAX_ENGINE_CC,
    ALLOWED_FUEL_TYPES;

    public boolean isNumeric() {
        return this != ALLOWED_FUEL_TYPES;
    }
}
