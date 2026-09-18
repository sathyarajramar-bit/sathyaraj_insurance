package com.insurance.quote.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The numbers below are computed by hand from the seeded MOTOR-CAR-COMP rates; any drift is a pricing bug. */
class PremiumCalculatorTest {

    private final PremiumCalculator calculator = new PremiumCalculator();

    /** Seeded car rates: 2.5% of IDV, min 2500, TP 3416, 2%/yr age loading max 20%, young <25 +10%, NCB max 50%, EV -15%, GST 18%. */
    static PricingInput.Rates carRates() {
        return new PricingInput.Rates(bd("2.5"), bd("2500"), bd("3416"), bd("2"), bd("20"), 25, bd("10"), bd("50"), bd("15"), bd("18"));
    }

    static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void comprehensiveWithoutLoadingsOrAddOns() {
        // IDV 600000 -> OD 15000; no age loading (0 years); driver 30; base = 15000 + 3416 = 18416; tax 18% = 3314.88
        PremiumResult r = calculator.calculate(new PricingInput("COMPREHENSIVE", bd("600000"), 0, "PETROL", 30, bd("0"), List.of(), carRates()));

        assertThat(r.ownDamagePremium()).isEqualByComparingTo("15000.00");
        assertThat(r.thirdPartyPremium()).isEqualByComparingTo("3416.00");
        assertThat(r.basePremium()).isEqualByComparingTo("18416.00");
        assertThat(r.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(r.taxAmount()).isEqualByComparingTo("3314.88");
        assertThat(r.finalPremium()).isEqualByComparingTo("21730.88");
    }

    @Test
    void loadingsAddOnsDiscountAndTaxAddUp() {
        // IDV 500000 -> OD 12500; 3 years -> +6% = 13250; driver 22 -> +10% = 14575
        // add-ons: 0.4% of IDV = 2000; 5% of base (14575+3416=17991) = 899.55; flat 499 -> 3398.55
        // NCB 20% of OD = 2915; taxable = 17991 + 3398.55 - 2915 = 18474.55; tax = 3325.42; final = 21799.97
        List<PricingInput.AddOnInput> addOns = List.of(
                new PricingInput.AddOnInput("ZERO_DEPRECIATION", "Zero dep", "PERCENT_OF_IDV", bd("0.4")),
                new PricingInput.AddOnInput("NCB_PROTECT", "NCB protect", "PERCENT_OF_BASE_PREMIUM", bd("5")),
                new PricingInput.AddOnInput("ROADSIDE_ASSISTANCE", "RSA", "FLAT", bd("499")));
        PremiumResult r = calculator.calculate(new PricingInput("COMPREHENSIVE", bd("500000"), 3, "PETROL", 22, bd("20"), addOns, carRates()));

        assertThat(r.ownDamagePremium()).isEqualByComparingTo("14575.00");
        assertThat(r.basePremium()).isEqualByComparingTo("17991.00");
        assertThat(r.addOns()).extracting(PremiumResult.AddOnPremium::premium)
                .containsExactly(bd("2000.00"), bd("899.55"), bd("499.00"));
        assertThat(r.addOnPremium()).isEqualByComparingTo("3398.55");
        assertThat(r.discountAmount()).isEqualByComparingTo("2915.00");
        assertThat(r.taxAmount()).isEqualByComparingTo("3325.42");
        assertThat(r.finalPremium()).isEqualByComparingTo("21799.97");
        assertThat(r.basePremium().add(r.addOnPremium()).subtract(r.discountAmount()).add(r.taxAmount()))
                .isEqualByComparingTo(r.finalPremium());
    }

    @Test
    void ageLoadingIsCappedAndNcbIsCappedAndEvDiscountApplies() {
        // 15 years -> 30% requested, capped at 20%: OD = 12500 * 1.2 = 15000; NCB 60 capped 50 -> 7500; EV 15% -> 2250 => 9750
        PremiumResult r = calculator.calculate(new PricingInput("COMPREHENSIVE", bd("500000"), 15, "ELECTRIC", 40, bd("60"), List.of(), carRates()));

        assertThat(r.ownDamagePremium()).isEqualByComparingTo("15000.00");
        assertThat(r.discountAmount()).isEqualByComparingTo("9750.00");
    }

    @Test
    void minimumBasePremiumApplies() {
        // IDV 50000 -> 1250 < min 2500
        PremiumResult r = calculator.calculate(new PricingInput("COMPREHENSIVE", bd("50000"), 0, "PETROL", 40, bd("0"), List.of(), carRates()));
        assertThat(r.ownDamagePremium()).isEqualByComparingTo("2500.00");
    }

    @Test
    void thirdPartyOnlyHasNoOwnDamageAndNoDiscounts() {
        PricingInput.Rates bikeRates = new PricingInput.Rates(bd("0"), bd("0"), bd("714"), bd("0"), bd("0"), 25, bd("0"), bd("0"), bd("0"), bd("18"));
        PremiumResult r = calculator.calculate(new PricingInput("THIRD_PARTY", bd("90000"), 2, "PETROL", 20, bd("30"),
                List.of(new PricingInput.AddOnInput("PA_PILLION", "Pillion", "FLAT", bd("150"))), bikeRates));

        assertThat(r.ownDamagePremium()).isEqualByComparingTo("0.00");
        assertThat(r.thirdPartyPremium()).isEqualByComparingTo("714.00");
        assertThat(r.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(r.addOnPremium()).isEqualByComparingTo("150.00");
        assertThat(r.taxAmount()).isEqualByComparingTo("155.52");
        assertThat(r.finalPremium()).isEqualByComparingTo("1019.52");
    }
}
