package com.insurance.quote.repository;

import com.insurance.quote.entity.Quote;
import com.insurance.quote.entity.QuoteStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class QuoteSpecifications {

    private QuoteSpecifications() {
    }

    public static Specification<Quote> withFilters(Long userId, Long customerId, QuoteStatus status, Long productId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (productId != null) {
                predicates.add(cb.equal(root.get("productId"), productId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
