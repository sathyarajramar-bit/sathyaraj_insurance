package com.insurance.product.dto;

import com.insurance.product.entity.AddOnPricingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddOnRequest(
        @NotBlank(message = "Add-on code is required")
        @Pattern(regexp = "^[A-Z0-9_]{2,40}$", message = "Add-on code must be upper case letters, digits or underscores") String code,
        @NotBlank(message = "Add-on name is required") @Size(max = 150) String name,
        @Size(max = 500) String description,
        @NotNull(message = "Add-on pricing type is required") AddOnPricingType pricingType,
        @NotNull(message = "Add-on rate is required") @PositiveOrZero BigDecimal rate,
        Boolean active) {
}
