package com.insurance.product.service;

import com.insurance.common.exception.DuplicateResourceException;
import com.insurance.common.exception.ValidationException;
import com.insurance.product.dto.AddOnRequest;
import com.insurance.product.dto.CoverageRequest;
import com.insurance.product.dto.EligibilityRuleRequest;
import com.insurance.product.dto.PricingRequest;
import com.insurance.product.dto.ProductRequest;
import com.insurance.product.entity.AddOnPricingType;
import com.insurance.product.entity.CoverageType;
import com.insurance.product.entity.ProductType;
import com.insurance.product.entity.RuleType;
import com.insurance.product.entity.SumInsuredType;
import com.insurance.product.entity.VehicleType;
import com.insurance.product.mapper.ProductMapper;
import com.insurance.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock private ProductRepository repository;

    private ProductService service() {
        return new ProductService(repository, Mappers.getMapper(ProductMapper.class), new ProductRequestValidator());
    }

    public static PricingRequest pricing() {
        BigDecimal z = BigDecimal.ZERO;
        return new PricingRequest(new BigDecimal("2.5"), new BigDecimal("2500"), new BigDecimal("3416"),
                new BigDecimal("2"), new BigDecimal("20"), 25, new BigDecimal("10"), new BigDecimal("50"), z, new BigDecimal("18"));
    }

    public static ProductRequest request(String code, ProductType type, VehicleType vehicleType,
                                         List<CoverageRequest> coverages, List<AddOnRequest> addOns,
                                         List<EligibilityRuleRequest> rules) {
        return new ProductRequest(code, "Name", null, type, vehicleType, CoverageType.COMPREHENSIVE, 12, true,
                LocalDate.of(2026, 1, 1), null, coverages, addOns, rules, pricing());
    }

    public static CoverageRequest coverage(String code) {
        return new CoverageRequest(code, code, null, SumInsuredType.IDV, null, true, 1);
    }

    @Test
    void duplicateCodeIsRejectedBeforeSaving() {
        when(repository.existsByCode("MOTOR-CAR-COMP")).thenReturn(true);

        assertThatThrownBy(() -> service().create(request("MOTOR-CAR-COMP", ProductType.MOTOR, VehicleType.CAR,
                List.of(coverage("OWN_DAMAGE")), null, null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void motorProductNeedsAVehicleType() {
        assertThatThrownBy(() -> service().create(request("X-1", ProductType.MOTOR, null, List.of(coverage("A")), null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Motor products must specify a vehicle type");
    }

    @Test
    void duplicateAddOnCodesAreRejected() {
        AddOnRequest addOn = new AddOnRequest("ZERO_DEP", "Zero dep", null, AddOnPricingType.FLAT, BigDecimal.ONE, true);
        assertThatThrownBy(() -> service().create(request("X-1", ProductType.MOTOR, VehicleType.CAR,
                List.of(coverage("A")), List.of(addOn, addOn), null)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Duplicate add-on code: ZERO_DEP");
    }

    @Test
    void numericRuleWithTextValueIsRejected() {
        EligibilityRuleRequest rule = new EligibilityRuleRequest(RuleType.MAX_VEHICLE_AGE_YEARS, "fifteen", "msg");
        assertThatThrownBy(() -> service().create(request("X-1", ProductType.MOTOR, VehicleType.CAR,
                List.of(coverage("A")), null, List.of(rule))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("requires a numeric value");
    }

    @Test
    void fixedSumInsuredCoverageNeedsAnAmount() {
        CoverageRequest fixed = new CoverageRequest("TP", "Third party", null, SumInsuredType.FIXED, null, true, 1);
        assertThatThrownBy(() -> service().create(request("X-1", ProductType.MOTOR, VehicleType.CAR, List.of(fixed), null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("FIXED sum insured but no amount");
    }
}
