package com.insurance.product.dto;

import com.insurance.product.entity.RuleType;

public record EligibilityRuleResponse(Long id, RuleType ruleType, String ruleValue, String message) {
}
