package com.insurance.product.repository;

import com.insurance.product.entity.CoverageType;
import com.insurance.product.entity.Product;
import com.insurance.product.entity.ProductType;
import com.insurance.product.entity.VehicleType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Dynamic WHERE clause for {@code GET /api/products?type=&vehicleType=&coverageType=&active=}. */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> withFilters(ProductType type, VehicleType vehicleType,
                                                     CoverageType coverageType, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) {
                predicates.add(cb.equal(root.get("productType"), type));
            }
            if (vehicleType != null) {
                predicates.add(cb.equal(root.get("vehicleType"), vehicleType));
            }
            if (coverageType != null) {
                predicates.add(cb.equal(root.get("coverageType"), coverageType));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
