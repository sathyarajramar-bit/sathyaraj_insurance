package com.insurance.product.service;

import com.insurance.common.exception.ValidationException;
import com.insurance.product.dto.AddOnRequest;
import com.insurance.product.dto.CoverageRequest;
import com.insurance.product.dto.EligibilityRuleRequest;
import com.insurance.product.dto.ProductRequest;
import com.insurance.product.entity.ProductType;
import com.insurance.product.entity.SumInsuredType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Business validation that Bean Validation cannot express (cross-field and cross-item rules). */
@Component
public class ProductRequestValidator {

    public void validate(ProductRequest request) {
        if (request.productType() == ProductType.MOTOR && request.vehicleType() == null) {
            throw new ValidationException("Motor products must specify a vehicle type");
        }
        if (request.productType() != ProductType.MOTOR && request.vehicleType() != null) {
            throw new ValidationException("Only motor products can specify a vehicle type");
        }
        if (request.effectiveTo() != null && !request.effectiveTo().isAfter(request.effectiveFrom())) {
            throw new ValidationException("Effective-to date must be after effective-from date");
        }
        requireUnique(request.coverages().stream().map(CoverageRequest::code).toList(), "coverage");
        if (request.addOns() != null) {
            requireUnique(request.addOns().stream().map(AddOnRequest::code).toList(), "add-on");
        }
        if (request.eligibilityRules() != null) {
            requireUnique(request.eligibilityRules().stream().map(r -> r.ruleType().name()).toList(), "eligibility rule");
            request.eligibilityRules().forEach(this::validateRuleValue);
        }
        for (CoverageRequest coverage : request.coverages()) {
            if (coverage.sumInsuredType() == SumInsuredType.FIXED && coverage.fixedSumInsured() == null) {
                throw new ValidationException("Coverage " + coverage.code() + " has a FIXED sum insured but no amount");
            }
        }
    }

    private void validateRuleValue(EligibilityRuleRequest rule) {
        if (rule.ruleType().isNumeric()) {
            try {
                new BigDecimal(rule.ruleValue().trim());
            } catch (NumberFormatException e) {
                throw new ValidationException("Rule " + rule.ruleType() + " requires a numeric value");
            }
        }
    }

    private static void requireUnique(List<String> codes, String what) {
        Set<String> seen = new HashSet<>();
        for (String code : codes) {
            if (!seen.add(code)) {
                throw new ValidationException("Duplicate " + what + " code: " + code);
            }
        }
    }
}
