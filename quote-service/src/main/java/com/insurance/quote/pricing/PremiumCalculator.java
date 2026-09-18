package com.insurance.quote.pricing;

import com.insurance.common.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The premium engine. Pure, deterministic and side-effect free: same input, same output, unit-tested to
 * the rupee. Rates come from product-service; the formula lives here so a pricing change is a config
 * change and a formula change is a code change with tests.
 *
 * <pre>
 *  ownDamage   = max(IDV x baseRate%, minBasePremium)              (COMPREHENSIVE / OWN_DAMAGE only)
 *                x (1 + min(vehicleAge x ageLoading%, maxAgeLoading%))
 *                x (1 + youngDriverLoading%)                       (driverAge below limit)
 *  thirdParty  = flat statutory amount                              (COMPREHENSIVE / THIRD_PARTY only)
 *  base        = ownDamage + thirdParty
 *  addOns      = sum(PERCENT_OF_IDV: IDV x rate% | PERCENT_OF_BASE_PREMIUM: base x rate% | FLAT: rate)
 *  discount    = ownDamage x min(ncb%, maxNcb%) + ownDamage x evDiscount%   (own damage only)
 *  tax         = (base + addOns - discount) x tax%
 *  final       = base + addOns - discount + tax
 * </pre>
 * All money is rounded HALF_UP to 2 decimals at each line so the breakdown adds up exactly.
 */
@Component
public class PremiumCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public PremiumResult calculate(PricingInput in) {
        PricingInput.Rates rates = in.rates();
        boolean ownDamageCovered = !"THIRD_PARTY".equals(in.coverageType());
        boolean thirdPartyCovered = !"OWN_DAMAGE".equals(in.coverageType());

        BigDecimal ownDamage = BigDecimal.ZERO;
        if (ownDamageCovered) {
            ownDamage = percentOf(in.idv(), rates.baseRatePercentOfIdv()).max(rates.minBasePremium());
            BigDecimal ageLoading = rates.vehicleAgeLoadingPercentPerYear().multiply(BigDecimal.valueOf(in.vehicleAgeYears()))
                    .min(rates.maxVehicleAgeLoadingPercent());
            ownDamage = ownDamage.add(percentOf(ownDamage, ageLoading));
            if (in.driverAge() < rates.youngDriverAgeLimit()) {
                ownDamage = ownDamage.add(percentOf(ownDamage, rates.youngDriverLoadingPercent()));
            }
            ownDamage = money(ownDamage);
        }
        BigDecimal thirdParty = thirdPartyCovered ? money(rates.thirdPartyPremium()) : money(BigDecimal.ZERO);
        BigDecimal base = ownDamage.add(thirdParty);

        List<PremiumResult.AddOnPremium> addOns = new ArrayList<>();
        BigDecimal addOnTotal = BigDecimal.ZERO;
        for (PricingInput.AddOnInput addOn : in.addOns()) {
            BigDecimal premium = money(switch (addOn.pricingType()) {
                case "PERCENT_OF_IDV" -> percentOf(in.idv(), addOn.rate());
                case "PERCENT_OF_BASE_PREMIUM" -> percentOf(base, addOn.rate());
                case "FLAT" -> addOn.rate();
                default -> throw new ValidationException("Unsupported add-on pricing type " + addOn.pricingType());
            });
            addOns.add(new PremiumResult.AddOnPremium(addOn.code(), addOn.name(), premium));
            addOnTotal = addOnTotal.add(premium);
        }

        BigDecimal discount = BigDecimal.ZERO;
        if (ownDamageCovered) {
            BigDecimal ncb = in.ncbPercent().min(rates.maxNcbDiscountPercent()).max(BigDecimal.ZERO);
            discount = percentOf(ownDamage, ncb);
            if ("ELECTRIC".equalsIgnoreCase(in.fuelType())) {
                discount = discount.add(percentOf(ownDamage, rates.electricVehicleDiscountPercent()));
            }
            discount = money(discount);
        }

        BigDecimal taxable = base.add(addOnTotal).subtract(discount);
        BigDecimal tax = money(percentOf(taxable, rates.taxPercent()));
        BigDecimal finalPremium = money(taxable.add(tax));

        return new PremiumResult(ownDamage, thirdParty, money(base), addOns, money(addOnTotal), discount, tax, finalPremium);
    }

    private static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(HUNDRED, 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
