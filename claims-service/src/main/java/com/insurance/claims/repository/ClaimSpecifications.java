package com.insurance.claims.repository;

import com.insurance.claims.entity.Claim;
import com.insurance.claims.entity.ClaimStatus;
import com.insurance.claims.entity.ClaimType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class ClaimSpecifications {

    private ClaimSpecifications() {
    }

    public static Specification<Claim> withFilters(Long userId, Long customerId, String policyNumber, ClaimStatus status,
                                                   ClaimType type, LocalDate incidentFrom, LocalDate incidentTo) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (userId != null) {
                p.add(cb.equal(root.get("userId"), userId));
            }
            if (customerId != null) {
                p.add(cb.equal(root.get("customerId"), customerId));
            }
            if (policyNumber != null && !policyNumber.isBlank()) {
                p.add(cb.equal(root.get("policyNumber"), policyNumber.trim()));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                p.add(cb.equal(root.get("claimType"), type));
            }
            if (incidentFrom != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("incidentDate"), incidentFrom));
            }
            if (incidentTo != null) {
                p.add(cb.lessThanOrEqualTo(root.get("incidentDate"), incidentTo));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
