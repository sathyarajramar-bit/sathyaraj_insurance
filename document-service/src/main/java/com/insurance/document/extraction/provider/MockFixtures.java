package com.insurance.document.extraction.provider;

import com.insurance.document.entity.DocumentType;
import com.insurance.document.extraction.field.ExtractionResult;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed sample values the mock provider returns. The keys are the ones the reference motor flow consumes from its
 * OCR server ({@code license_number}, {@code license_issue_date}, {@code traffic_plate_number}, {@code emirate_id},
 * ...), so a form that is wired against the mock keeps working when the LOCAL provider is switched on.
 *
 * <p>Every value is obviously synthetic. Nothing here is read from the uploaded file.
 */
final class MockFixtures {

    static final String SIMULATED_WARNING =
            "Simulated response: fixed sample values from the mock provider, not read from the uploaded file";

    /** Fixture fields are "read" perfectly, so the confidence is 1.0. */
    private static final double FIXTURE = 1.0;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private MockFixtures() {
    }

    static ExtractionResult build(DocumentType type, MockScenario scenario, Clock clock) {
        boolean missing = scenario == MockScenario.MISSING_FIELDS;
        return switch (type) {
            case DRIVING_LICENSE -> drivingLicence(missing, clock);
            case VEHICLE_REGISTRATION -> vehicleRegistration(missing);
            case EMIRATES_ID, ID_PROOF -> emiratesId(missing);
            default -> generic(missing);
        };
    }

    private static ExtractionResult drivingLicence(boolean missing, Clock clock) {
        ExtractionResult r = new ExtractionResult();
        LocalDate issue = LocalDate.of(2015, 9, 18);
        r.put("license_authority_id", "MOCK00001", FIXTURE)
                .put("license_number", "0194428", FIXTURE)
                .put("name_english", "SAMPLE HOLDER NAME", FIXTURE)
                .put("nationality_english", "SAMPLE", FIXTURE)
                .put("date_of_birth", "01/01/1990", FIXTURE);
        if (missing) {
            r.missing("license_issue_date");
        } else {
            r.put("license_issue_date", issue.format(DMY), FIXTURE);
        }
        r.put("license_expire_date", "17/09/2030", FIXTURE)
                .put("issue_place_english", "Sharjah", FIXTURE)
                .put("issue_place_arabic", "الشارقة", FIXTURE);
        if (missing) {
            r.missing("traffic_code");
            r.missing("driving_experience_years");
            r.warn("Required field missing: license_issue_date");
            r.warn("Required field missing: traffic_code");
        } else {
            r.put("traffic_code", "3070265345", FIXTURE);
            r.put("driving_experience_years", (int) ChronoUnit.YEARS.between(issue, LocalDate.now(clock)), FIXTURE);
        }
        r.put("permitted_vehicle", Map.of("english", List.of("Light Vehicle"), "arabic", List.of("مركبة خفيفة")), FIXTURE)
                .put("transmission_type", "AUTOMATIC", FIXTURE);
        return r;
    }

    private static ExtractionResult vehicleRegistration(boolean missing) {
        ExtractionResult r = new ExtractionResult();
        r.put("traffic_plate_number", "A 12345", FIXTURE)
                .put("chassis_number", "MOCKCHASSIS0000001", FIXTURE)
                .put("model_year", "2021", FIXTURE)
                .put("engine_number", "MOCKENG0001", FIXTURE)
                .put("vehicle_gross_weight", "1850", FIXTURE)
                .put("policy_number", "P-MOCK-0001", FIXTURE)
                .put("issued_place_english", "Dubai", FIXTURE);
        if (missing) {
            r.missing("traffic_code");
            r.missing("vehicle_registered");
            r.warn("Required field missing: traffic_code");
            r.warn("Required field missing: vehicle_registered");
        } else {
            r.put("traffic_code", "3070265345", FIXTURE).put("vehicle_registered", "15/03/2021", FIXTURE);
        }
        return r;
    }

    private static ExtractionResult emiratesId(boolean missing) {
        ExtractionResult r = new ExtractionResult();
        r.put("emirate_id", "784-1990-0000000-1", FIXTURE)
                .put("name_english", "SAMPLE HOLDER NAME", FIXTURE)
                .put("gender", "M", FIXTURE)
                .put("card_number", "000000000", FIXTURE)
                .put("occupation_english", "SAMPLE OCCUPATION", FIXTURE)
                .put("issuing_place_english", "Dubai", FIXTURE);
        if (missing) {
            r.missing("date_of_birth");
            r.warn("Required field missing: date_of_birth");
        } else {
            r.put("date_of_birth", "01/01/1990", FIXTURE);
        }
        return r;
    }

    private static ExtractionResult generic(boolean missing) {
        ExtractionResult r = new ExtractionResult();
        Map<String, String> keyValues = new LinkedHashMap<>();
        keyValues.put("document", "SAMPLE DOCUMENT");
        keyValues.put("reference", "MOCK-0001");
        r.put("key_values", keyValues, FIXTURE)
                .put("dates", missing ? List.of() : List.of("01/01/2026"), FIXTURE)
                .put("numbers", List.of("0001"), FIXTURE)
                .put("lines", List.of("SAMPLE DOCUMENT", "Reference: MOCK-0001"), FIXTURE)
                .put("raw_text", "SAMPLE DOCUMENT\nReference: MOCK-0001", FIXTURE);
        if (missing) {
            r.warn("No dates found in the document");
        }
        return r;
    }
}
