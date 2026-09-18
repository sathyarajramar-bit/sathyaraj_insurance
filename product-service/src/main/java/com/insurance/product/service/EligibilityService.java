package com.insurance.product.service;

import com.insurance.product.dto.EligibilityResponse;
import com.insurance.product.dto.RiskProfileRequest;
import com.insurance.product.eligibility.EligibilityEvaluator;
import com.insurance.product.eligibility.EligibilityResult;
import com.insurance.product.eligibility.RiskProfile;
import com.insurance.product.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Application service: loads the aggregate and delegates to the pure evaluator. Not cached (input specific). */
@Service
@RequiredArgsConstructor
public class EligibilityService {

    private final ProductService productService;
    private final EligibilityEvaluator evaluator;

    @Transactional(readOnly = true)
    public EligibilityResponse check(Long productId, RiskProfileRequest request) {
        Product product = productService.find(productId);
        RiskProfile profile = new RiskProfile(request.vehicleType(), request.vehicleAgeYears(), request.idv(),
                request.engineCapacityCc(), request.fuelType(), request.driverAge());
        EligibilityResult result = evaluator.evaluate(product, profile, LocalDate.now());
        return new EligibilityResponse(product.getId(), product.getCode(), result.eligible(), result.violations());
    }
}
