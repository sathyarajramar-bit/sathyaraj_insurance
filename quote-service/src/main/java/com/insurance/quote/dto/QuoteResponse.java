package com.insurance.quote.dto;

import com.insurance.quote.entity.QuoteStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record QuoteResponse(Long id, String quoteNumber, QuoteStatus status, Long userId, Long customerId,
                            Long productId, String productCode, String productName, String coverageType, Integer termMonths,
                            Long vehicleId, String registrationNumber, String vehicleType, String make, String model,
                            String fuelType, Integer manufacturingYear, Integer engineCapacityCc,
                            BigDecimal idv, Integer driverAge, BigDecimal ncbPercent,
                            BigDecimal ownDamagePremium, BigDecimal thirdPartyPremium, BigDecimal basePremium,
                            List<QuoteAddOnResponse> addOns, BigDecimal addOnPremium, BigDecimal discountAmount,
                            BigDecimal taxAmount, BigDecimal finalPremium,
                            Instant validUntil, Instant acceptedAt, Instant cancelledAt, Instant createdAt) {
}
