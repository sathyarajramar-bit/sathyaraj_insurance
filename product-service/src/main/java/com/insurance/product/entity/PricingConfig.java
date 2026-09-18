package com.insurance.product.entity;

import com.insurance.common.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Parameters of the premium formula; the formula itself lives in quote-service's calculation engine. */
@Entity
@Table(name = "product_pricing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PricingConfig extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "base_rate_percent_of_idv", nullable = false, precision = 8, scale = 4)
    private BigDecimal baseRatePercentOfIdv;

    @Column(name = "min_base_premium", nullable = false, precision = 12, scale = 2)
    private BigDecimal minBasePremium;

    @Column(name = "third_party_premium", nullable = false, precision = 12, scale = 2)
    private BigDecimal thirdPartyPremium;

    @Column(name = "vehicle_age_loading_percent_per_year", nullable = false, precision = 8, scale = 4)
    private BigDecimal vehicleAgeLoadingPercentPerYear;

    @Column(name = "max_vehicle_age_loading_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal maxVehicleAgeLoadingPercent;

    @Column(name = "young_driver_age_limit", nullable = false)
    private Integer youngDriverAgeLimit;

    @Column(name = "young_driver_loading_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal youngDriverLoadingPercent;

    @Column(name = "max_ncb_discount_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal maxNcbDiscountPercent;

    @Column(name = "electric_vehicle_discount_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal electricVehicleDiscountPercent;

    @Column(name = "tax_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal taxPercent;
}
