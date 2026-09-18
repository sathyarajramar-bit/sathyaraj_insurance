package com.insurance.product.dto;

import com.insurance.product.entity.RuleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EligibilityRuleRequest(
        @NotNull(message = "Rule type is required") RuleType ruleType,
        @NotBlank(message = "Rule value is required") @Size(max = 100) String ruleValue,
        @NotBlank(message = "Rule message is required") @Size(max = 255) String message) {
}
