package com.insurance.document.extraction.provider;

import com.insurance.common.exception.ValidationException;

import java.util.Arrays;
import java.util.Locale;

/** What the mock provider should simulate for one request ({@code ?scenario=}). */
public enum MockScenario {
    /** Every field of the fixture is present. */
    SUCCESS,
    /** Some fields the real extractor would flag as required are null, with the matching warnings. */
    MISSING_FIELDS,
    /** The provider fails the way a broken OCR engine would: HTTP 422, messageType E, errorCode OCR_FAILED. */
    PROCESSING_FAILURE;

    /** Case-insensitive parse; blank means "use the configured default"; anything else is a 400. */
    public static MockScenario from(String raw, MockScenario fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String wanted = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(s -> s.name().equals(wanted)).findFirst()
                .orElseThrow(() -> new ValidationException("Unknown scenario '" + raw + "'; use one of " + Arrays.toString(values())));
    }
}
