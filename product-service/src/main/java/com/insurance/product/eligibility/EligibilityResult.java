package com.insurance.product.eligibility;

import java.util.List;

public record EligibilityResult(boolean eligible, List<String> violations) {

    public static EligibilityResult of(List<String> violations) {
        return new EligibilityResult(violations.isEmpty(), List.copyOf(violations));
    }
}
