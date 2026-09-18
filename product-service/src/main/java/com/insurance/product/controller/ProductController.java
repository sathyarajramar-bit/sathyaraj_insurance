package com.insurance.product.controller;

import com.insurance.common.dto.PageResponse;
import com.insurance.common.security.CurrentUser;
import com.insurance.product.dto.EligibilityResponse;
import com.insurance.product.dto.PricingResponse;
import com.insurance.product.dto.ProductRequest;
import com.insurance.product.dto.ProductResponse;
import com.insurance.product.dto.ProductSummaryResponse;
import com.insurance.product.dto.RiskProfileRequest;
import com.insurance.product.entity.CoverageType;
import com.insurance.product.entity.ProductType;
import com.insurance.product.entity.VehicleType;
import com.insurance.product.service.EligibilityService;
import com.insurance.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Insurance product catalogue")
public class ProductController {

    private final ProductService productService;
    private final EligibilityService eligibilityService;

    @GetMapping
    @Operation(summary = "Browse the catalogue (public). Non-admins only ever see active products.", security = {})
    public PageResponse<ProductSummaryResponse> search(
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false) CoverageType coverageType,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        boolean admin = CurrentUser.get().map(u -> u.hasRole("ADMIN")).orElse(false);
        Boolean effectiveActive = admin ? active : Boolean.TRUE;
        return productService.search(type, vehicleType, coverageType, effectiveActive, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Product details with coverages, add-ons and eligibility rules (public)", security = {})
    public ProductResponse get(@PathVariable Long id) {
        return productService.getById(id);
    }

    @GetMapping("/{id}/pricing")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','SERVICE')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Pricing parameters (read by quote-service)")
    public PricingResponse pricing(@PathVariable Long id) {
        return productService.getPricing(id);
    }

    @PostMapping("/{id}/eligibility-check")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Evaluate the product's eligibility rules against a risk profile")
    public EligibilityResponse eligibility(@PathVariable Long id, @Valid @RequestBody RiskProfileRequest request) {
        return eligibilityService.check(id, request);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a product with its coverages, add-ons, rules and pricing")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Replace a product aggregate (code is immutable)")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Deactivate a product (soft delete: quotes/policies keep referencing it)")
    public ProductResponse deactivate(@PathVariable Long id) {
        return productService.setActive(id, false);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Re-activate a product")
    public ProductResponse activate(@PathVariable Long id) {
        return productService.setActive(id, true);
    }
}
