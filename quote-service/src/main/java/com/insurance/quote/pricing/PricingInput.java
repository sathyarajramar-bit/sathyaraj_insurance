package com.insurance.quote.pricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the engine needs, already resolved by the service (no remote calls inside the engine).
 *
 * @param coverageType    THIRD_PARTY | COMPREHENSIVE | OWN_DAMAGE
 * @param idv             insured declared value of the vehicle
 * @param vehicleAgeYears current year minus manufacturing year
 * @param fuelType        PETROL, DIESEL, CNG, ELECTRIC, HYBRID
 * @param driverAge       age of the main driver
 * @param ncbPercent      no-claim bonus the customer is entitled to (0-50)
 * @param addOns          selected add-ons with their product pricing rule
 */
public record PricingInput(String coverageType, BigDecimal idv, int vehicleAgeYears, String fuelType, int driverAge,
                           BigDecimal ncbPercent, List<AddOnInput> addOns, Rates rates) {

    /** Pricing parameters as configured on the product (product-service {@code /pricing}). */
    public record Rates(BigDecimal baseRatePercentOfIdv, BigDecimal minBasePremium, BigDecimal thirdPartyPremium,
                        BigDecimal vehicleAgeLoadingPercentPerYear, BigDecimal maxVehicleAgeLoadingPercent,
                        int youngDriverAgeLimit, BigDecimal youngDriverLoadingPercent, BigDecimal maxNcbDiscountPercent,
                        BigDecimal electricVehicleDiscountPercent, BigDecimal taxPercent) {
    }

    public record AddOnInput(String code, String name, String pricingType, BigDecimal rate) {
    }
}
