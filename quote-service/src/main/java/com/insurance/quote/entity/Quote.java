package com.insurance.quote.entity;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A priced offer valid until {@code validUntil}. Everything that influenced the premium is copied in
 * (product, vehicle facts, driver age, NCB, breakdown) so the number can be explained months later.
 */
@Entity
@Table(name = "quotes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quote extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quote_number", nullable = false, unique = true, length = 30)
    private String quoteNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteStatus status;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_code", nullable = false, length = 40)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "coverage_type", nullable = false, length = 20)
    private String coverageType;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "registration_number", nullable = false, length = 20)
    private String registrationNumber;

    @Column(name = "vehicle_type", nullable = false, length = 10)
    private String vehicleType;

    @Column(nullable = false, length = 50)
    private String make;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(name = "fuel_type", nullable = false, length = 20)
    private String fuelType;

    @Column(name = "manufacturing_year", nullable = false)
    private Integer manufacturingYear;

    @Column(name = "engine_capacity_cc", nullable = false)
    private Integer engineCapacityCc;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal idv;

    @Column(name = "driver_age", nullable = false)
    private Integer driverAge;

    @Column(name = "ncb_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal ncbPercent;

    @Column(name = "own_damage_premium", nullable = false, precision = 12, scale = 2)
    private BigDecimal ownDamagePremium;

    @Column(name = "third_party_premium", nullable = false, precision = 12, scale = 2)
    private BigDecimal thirdPartyPremium;

    @Column(name = "base_premium", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePremium;

    @Column(name = "add_on_premium", nullable = false, precision = 12, scale = 2)
    private BigDecimal addOnPremium;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "final_premium", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalPremium;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("code ASC")
    @BatchSize(size = 20)
    @Builder.Default
    private List<QuoteAddOn> addOns = new ArrayList<>();

    public void addAddOn(QuoteAddOn addOn) {
        addOn.setQuote(this);
        addOns.add(addOn);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isExpired(Instant now) {
        return status == QuoteStatus.EXPIRED || (status == QuoteStatus.GENERATED && validUntil.isBefore(now));
    }
}
