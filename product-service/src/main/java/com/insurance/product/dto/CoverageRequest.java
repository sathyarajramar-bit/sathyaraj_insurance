package com.insurance.product.dto;

import com.insurance.product.entity.SumInsuredType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CoverageRequest(
        @NotBlank(message = "Coverage code is required")
        @Pattern(regexp = "^[A-Z0-9_]{2,40}$", message = "Coverage code must be upper case letters, digits or underscores") String code,
        @NotBlank(message = "Coverage name is required") @Size(max = 150) String name,
        @Size(max = 500) String description,
        @NotNull(message = "Sum insured type is required") SumInsuredType sumInsuredType,
        @PositiveOrZero BigDecimal fixedSumInsured,
        @NotNull(message = "mandatory flag is required") Boolean mandatory,
        Integer displayOrder) {
}
