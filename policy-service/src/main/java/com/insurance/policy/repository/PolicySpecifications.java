package com.insurance.policy.repository;

import com.insurance.policy.entity.Policy;
import com.insurance.policy.entity.PolicyStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class PolicySpecifications {

    private PolicySpecifications() {
    }

    public static Specification<Policy> withFilters(Long userId, Long customerId, PolicyStatus status, String registrationNumber) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (userId != null) {
                p.add(cb.equal(root.get("userId"), userId));
            }
            if (customerId != null) {
                p.add(cb.equal(root.get("customerId"), customerId));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (registrationNumber != null && !registrationNumber.isBlank()) {
                p.add(cb.equal(root.get("registrationNumber"), registrationNumber.trim().toUpperCase()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
