package com.insurance.customer.repository;

import com.insurance.customer.entity.Customer;
import com.insurance.customer.entity.KycStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Composable filters for {@code GET /api/customers?name=&email=&kycStatus=}.
 *
 * <p>Interview notes: derived query methods explode combinatorially with optional filters
 * (findByNameAndEmail, findByName, findByEmail ...). A {@link Specification} builds the WHERE clause
 * dynamically from only the filters that were supplied, in one type-safe query. Alternatives:
 * Querydsl, a native query with {@code (:x IS NULL OR col = :x)} conditions.
 */
public final class CustomerSpecifications {

    private CustomerSpecifications() {
    }

    public static Specification<Customer> withFilters(String name, String email, KycStatus kycStatus) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isBlank()) {
                String pattern = "%" + name.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), pattern),
                        cb.like(cb.lower(root.get("lastName")), pattern)));
            }
            if (email != null && !email.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("email")), "%" + email.trim().toLowerCase() + "%"));
            }
            if (kycStatus != null) {
                predicates.add(cb.equal(root.get("kycStatus"), kycStatus));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
