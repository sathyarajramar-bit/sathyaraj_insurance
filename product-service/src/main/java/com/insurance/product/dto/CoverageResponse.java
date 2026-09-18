package com.insurance.product.dto;

import com.insurance.product.entity.SumInsuredType;

import java.math.BigDecimal;

public record CoverageResponse(Long id, String code, String name, String description, SumInsuredType sumInsuredType,
                               BigDecimal fixedSumInsured, boolean mandatory, int displayOrder) {
}
