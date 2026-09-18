package com.insurance.product.dto;

import com.insurance.product.entity.CoverageType;
import com.insurance.product.entity.ProductType;
import com.insurance.product.entity.VehicleType;

import java.time.LocalDate;

/** Catalogue row: no child collections, so listing pages never trigger extra queries. */
public record ProductSummaryResponse(Long id, String code, String name, String description, ProductType productType,
                                     VehicleType vehicleType, CoverageType coverageType, Integer termMonths,
                                     boolean active, LocalDate effectiveFrom, LocalDate effectiveTo) {
}
