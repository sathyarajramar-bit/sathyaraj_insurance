package com.insurance.payment.repository;

import com.insurance.payment.entity.Payment;
import com.insurance.payment.entity.PaymentStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    public static Specification<Payment> withFilters(Long userId, String proposalNumber, PaymentStatus status) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (userId != null) {
                p.add(cb.equal(root.get("userId"), userId));
            }
            if (proposalNumber != null && !proposalNumber.isBlank()) {
                p.add(cb.equal(root.get("proposalNumber"), proposalNumber));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
