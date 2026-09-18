package com.insurance.product.dto;

import java.util.List;

public record EligibilityResponse(Long productId, String productCode, boolean eligible, List<String> violations) {
}
