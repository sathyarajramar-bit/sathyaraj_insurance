package com.insurance.policy.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Year;
import java.time.ZoneOffset;

/** {@code PL-MOTOR-2026-7K3P9XQ2}: line of business + year for humans, random suffix against enumeration. */
@Component
public class PolicyNumberGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    public String next(String productCode) {
        String line = productCode.contains("-") ? productCode.substring(0, productCode.indexOf('-')) : productCode;
        StringBuilder suffix = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return "PL-" + line + "-" + Year.now(ZoneOffset.UTC) + "-" + suffix;
    }
}
