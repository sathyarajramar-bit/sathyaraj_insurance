package com.insurance.quote.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param productId   catalogue product to quote
 * @param vehicleId   one of the customer's vehicles (customer-service); IDV = the vehicle's current value
 * @param addOnCodes  add-ons selected from the product (validated against the product)
 * @param driverAge   optional; defaults to the age derived from the customer's date of birth
 * @param ncbPercent  no-claim bonus claimed by the customer, 0-50 (capped by the product)
 * @param customerId  AGENT/ADMIN only: quote on behalf of this customer (customers always quote for themselves)
 */
public record QuoteRequest(
        @NotNull(message = "productId is required") Long productId,
        @NotNull(message = "vehicleId is required") Long vehicleId,
        @Size(max = 10, message = "At most 10 add-ons") List<String> addOnCodes,
        @Min(value = 16, message = "Driver must be at least 16") @Max(value = 100, message = "Driver age is invalid") Integer driverAge,
        @DecimalMin(value = "0", message = "NCB cannot be negative") @DecimalMax(value = "50", message = "NCB cannot exceed 50%") BigDecimal ncbPercent,
        Long customerId) {
}
