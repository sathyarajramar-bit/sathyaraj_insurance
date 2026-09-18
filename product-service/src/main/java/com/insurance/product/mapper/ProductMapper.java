package com.insurance.product.mapper;

import com.insurance.product.dto.AddOnRequest;
import com.insurance.product.dto.AddOnResponse;
import com.insurance.product.dto.CoverageRequest;
import com.insurance.product.dto.CoverageResponse;
import com.insurance.product.dto.EligibilityRuleRequest;
import com.insurance.product.dto.EligibilityRuleResponse;
import com.insurance.product.dto.PricingRequest;
import com.insurance.product.dto.PricingResponse;
import com.insurance.product.dto.ProductRequest;
import com.insurance.product.dto.ProductResponse;
import com.insurance.product.dto.ProductSummaryResponse;
import com.insurance.product.entity.AddOn;
import com.insurance.product.entity.Coverage;
import com.insurance.product.entity.EligibilityRule;
import com.insurance.product.entity.PricingConfig;
import com.insurance.product.entity.Product;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * Compile-time generated mappings. The aggregate's children are mapped by MapStruct; wiring them to
 * the parent (back references, orphan removal) is the service's job via {@code Product.replace*}.
 */
// Builders are disabled: MapStruct would read the Lombok builder method addOns(...) as an "adder" for a
// property named "ons". With plain setters the entity property names are unambiguous.
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ProductMapper {

    ProductSummaryResponse toSummary(Product product);

    ProductResponse toResponse(Product product);

    CoverageResponse toResponse(Coverage coverage);

    AddOnResponse toResponse(AddOn addOn);

    EligibilityRuleResponse toResponse(EligibilityRule rule);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productCode", source = "product.code")
    PricingResponse toResponse(PricingConfig pricing);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "coverages", ignore = true)
    @Mapping(target = "addOns", ignore = true)
    @Mapping(target = "eligibilityRules", ignore = true)
    @Mapping(target = "pricing", ignore = true)
    @Mapping(target = "active", source = "active", defaultValue = "true")
    Product toEntity(ProductRequest request);

    /** Scalar fields only; collections/pricing are replaced by the service so orphanRemoval works. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "coverages", ignore = true)
    @Mapping(target = "addOns", ignore = true)
    @Mapping(target = "eligibilityRules", ignore = true)
    @Mapping(target = "pricing", ignore = true)
    @Mapping(target = "active", source = "active", defaultValue = "true")
    void updateEntity(ProductRequest request, @MappingTarget Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "displayOrder", source = "displayOrder", defaultValue = "0")
    Coverage toEntity(CoverageRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "active", source = "active", defaultValue = "true")
    AddOn toEntity(AddOnRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    EligibilityRule toEntity(EligibilityRuleRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    PricingConfig toEntity(PricingRequest request);

    /** In-place updates keep child ids stable (quotes reference add-on ids) and avoid delete+insert on the same unique key. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "displayOrder", source = "displayOrder", defaultValue = "0")
    void updateEntity(CoverageRequest request, @MappingTarget Coverage coverage);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "active", source = "active", defaultValue = "true")
    void updateEntity(AddOnRequest request, @MappingTarget AddOn addOn);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    void updateEntity(EligibilityRuleRequest request, @MappingTarget EligibilityRule rule);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    void updateEntity(PricingRequest request, @MappingTarget PricingConfig pricing);
}
