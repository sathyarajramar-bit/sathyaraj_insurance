package com.insurance.product.entity;

import com.insurance.common.jpa.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate root of the catalogue. Coverages, add-ons, rules and pricing have no life of their own:
 * they are created, replaced and deleted through the product ({@code cascade = ALL, orphanRemoval}).
 *
 * <p>{@code @BatchSize} loads the three child collections with IN-queries instead of one query per
 * product (N+1); a JOIN FETCH of several Lists is not possible (MultipleBagFetchException) and would
 * multiply rows anyway. The catalogue listing uses summaries that never touch the collections.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 10)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_type", nullable = false, length = 20)
    private CoverageType coverageType;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, code ASC")
    @BatchSize(size = 20)
    @Builder.Default
    private List<Coverage> coverages = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("code ASC")
    @BatchSize(size = 20)
    @Builder.Default
    private List<AddOn> addOns = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ruleType ASC")
    @BatchSize(size = 20)
    @Builder.Default
    private List<EligibilityRule> eligibilityRules = new ArrayList<>();

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private PricingConfig pricing;

    /** Replaces children in place so Hibernate deletes the removed ones (orphanRemoval) and keeps ids of the list. */
    public void replaceCoverages(List<Coverage> replacement) {
        coverages.clear();
        replacement.forEach(c -> { c.setProduct(this); coverages.add(c); });
    }

    public void replaceAddOns(List<AddOn> replacement) {
        addOns.clear();
        replacement.forEach(a -> { a.setProduct(this); addOns.add(a); });
    }

    public void replaceEligibilityRules(List<EligibilityRule> replacement) {
        eligibilityRules.clear();
        replacement.forEach(r -> { r.setProduct(this); eligibilityRules.add(r); });
    }

    public void attachPricing(PricingConfig replacement) {
        replacement.setProduct(this);
        this.pricing = replacement;
    }

    public boolean isSellableOn(LocalDate date) {
        return active && !date.isBefore(effectiveFrom) && (effectiveTo == null || !date.isAfter(effectiveTo));
    }
}
