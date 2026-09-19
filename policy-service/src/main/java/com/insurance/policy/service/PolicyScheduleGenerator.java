package com.insurance.policy.service;

import com.insurance.policy.entity.Policy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Renders the policy schedule (the customer's certificate). Plain text today; a PDF renderer slots in here. */
@Component
public class PolicyScheduleGenerator {

    public String renderBase64(Policy p) {
        String text = """
                INSURANCE POLICY SCHEDULE
                =========================
                Policy number      : %s
                Status             : %s
                Product            : %s (%s)
                Coverage           : %s
                Policy holder      : %s <%s>
                Vehicle            : %s (vehicle id %d)
                Insured value (IDV): %s
                Premium paid       : %s
                Period of insurance: %s to %s
                Nominee            : %s (%s)
                Payment reference  : %s
                Issued at          : %s
                """.formatted(p.getPolicyNumber(), p.getStatus(), p.getProductName(), p.getProductCode(), p.getCoverageType(),
                p.getHolderName(), p.getHolderEmail(), p.getRegistrationNumber(), p.getVehicleId(), p.getIdv(), p.getPremiumAmount(),
                p.getStartDate(), p.getEndDate(), p.getNomineeName() == null ? "-" : p.getNomineeName(),
                p.getNomineeRelationship() == null ? "-" : p.getNomineeRelationship(), p.getPaymentReference(), p.getIssuedAt());
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
