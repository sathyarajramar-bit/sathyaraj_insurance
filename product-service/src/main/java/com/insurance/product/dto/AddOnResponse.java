package com.insurance.product.dto;

import com.insurance.product.entity.AddOnPricingType;

import java.math.BigDecimal;

public record AddOnResponse(Long id, String code, String name, String description, AddOnPricingType pricingType,
                            BigDecimal rate, boolean active) {
}
