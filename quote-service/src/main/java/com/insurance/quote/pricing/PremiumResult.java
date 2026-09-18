package com.insurance.quote.pricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * The breakdown shown to the customer and stored on the quote:
 * base (own damage + third party) + add-ons - discount + tax = final.
 */
public record PremiumResult(BigDecimal ownDamagePremium, BigDecimal thirdPartyPremium, BigDecimal basePremium,
                            List<AddOnPremium> addOns, BigDecimal addOnPremium, BigDecimal discountAmount,
                            BigDecimal taxAmount, BigDecimal finalPremium) {

    public record AddOnPremium(String code, String name, BigDecimal premium) {
    }
}
