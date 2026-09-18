package com.insurance.quote.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Human-readable, non-guessable business key: {@code QT-20260918-7K3P9X}. The date helps support staff;
 * the random suffix (36^6 = 2.2 billion per day) makes enumeration impractical. The unique index is the
 * final guard: a collision surfaces as 409 and the customer simply retries.
 */
@Component
public class QuoteNumberGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return "QT-" + LocalDate.now(ZoneOffset.UTC).format(DATE) + "-" + suffix;
    }
}
