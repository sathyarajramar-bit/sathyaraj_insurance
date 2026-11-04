package com.insurance.document.extraction.field;

import com.insurance.document.extraction.source.ExtractedText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure text in, fields out: no Spring, no PDF, no OCR. The text mimics what PDFBox / Tesseract return for a card. */
class DrivingLicenseExtractorTest {

    private final DrivingLicenseExtractor extractor = new DrivingLicenseExtractor();

    static final String SHARJAH_CARD = """
            UNITED ARAB EMIRATES    الإمارات العربية المتحدة
            DRIVING LICENSE   رخصة قيادة
            License No. 0194428   رقم الرخصة
            Name: Light Vehicle R EPM ANWAR UL HAQ FAZAL HAQ FAZAL HAQ   مركبة خفيفة R EPM انور الحق فضل الحق فضل الحق
            Nationality: PAKISTAN   باكستان
            Date of Birth: 01/01/1962
            Issue Date: 18/09/1995
            Expiry Date: 07/01/2031
            Place of Issue: Sharjah   الشارقة
            Traffic Code No. 3070265345
            Permitted Vehicles: Light Vehicle   مركبة خفيفة
            Automatic
            SHTR24716
            """;

    private static ExtractedText text(String s, Double ocrConfidence) {
        return new ExtractedText(List.of(s), ocrConfidence == null ? ExtractedText.Source.TEXT_LAYER : ExtractedText.Source.OCR, ocrConfidence);
    }

    @Test
    void readsEveryFieldOfTheSampleCard() {
        ExtractionResult r = extractor.extract(text(SHARJAH_CARD, null));
        Map<String, Object> f = r.fields();

        assertThat(f).containsEntry("license_authority_id", "SHTR24716")
                .containsEntry("license_number", "0194428")
                .containsEntry("license_issue_date", "18/09/1995")
                .containsEntry("license_expire_date", "07/01/2031")
                .containsEntry("date_of_birth", "01/01/1962")
                .containsEntry("issue_place_english", "Sharjah")
                .containsEntry("issue_place_arabic", "الشارقة")
                .containsEntry("nationality_english", "PAKISTAN")
                .containsEntry("nationality_arabic", "باكستان")
                .containsEntry("traffic_code", "3070265345")
                .containsEntry("transmission_type", "AUTOMATIC");
        // the vehicle class glued onto the name by the layout is stripped from both scripts
        assertThat(f).containsEntry("name_english", "ANWAR UL HAQ FAZAL HAQ FAZAL HAQ")
                .containsEntry("name_arabic", "انور الحق فضل الحق فضل الحق");
        @SuppressWarnings("unchecked")
        Map<String, List<String>> permitted = (Map<String, List<String>>) f.get("permitted_vehicle");
        assertThat(permitted.get("english")).containsExactly("Light Vehicle");
        assertThat(permitted.get("arabic")).containsExactly("مركبة خفيفة");

        assertThat(r.fieldConfidence().get("license_number")).isEqualTo(0.95);
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void fallsBackToChronologicalDatesAndEmirateNamesWhenLabelsAreGarbled() {
        String garbled = """
                Lic No 7788991
                ANWAR UL HAQ
                01/01/1980  12/05/2015  11/05/2025
                Dubai
                """;
        ExtractionResult r = extractor.extract(text(garbled, 0.8));
        assertThat(r.fields()).containsEntry("date_of_birth", "01/01/1980")
                .containsEntry("license_issue_date", "12/05/2015")
                .containsEntry("license_expire_date", "11/05/2025")
                .containsEntry("issue_place_english", "Dubai")
                .containsEntry("issue_place_arabic", "دبي");
        assertThat(r.fields().get("name_english")).isNull();           // no "Name" label: not guessed
        assertThat(r.warnings()).anySatisfy(w -> assertThat(w).contains("chronological"))
                .anySatisfy(w -> assertThat(w).contains("name_english"))
                .anySatisfy(w -> assertThat(w).contains("expired"));
        // inferred (0.6) scaled by OCR confidence 0.8 -> 0.6 * 0.9 = 0.54
        assertThat(r.fieldConfidence().get("date_of_birth")).isEqualTo(0.54);
    }

    @Test
    void rejectsImpossibleDatesAndFlagsOrderProblems() {
        String bad = """
                License No. 123456
                Name: SOME ONE
                Date of Birth: 31/02/1990
                Issue Date: 10/10/2030
                Expiry Date: 10/10/2020
                """;
        ExtractionResult r = extractor.extract(text(bad, null));
        assertThat(r.fields().get("date_of_birth")).isNull();          // 31 Feb is not a date
        assertThat(r.warnings()).anySatisfy(w -> assertThat(w).contains("not after"));
    }

    @Test
    void stripsVehicleClassPrefixes() {
        assertThat(DrivingLicenseExtractor.stripVehicleClasses("Light Vehicle R EPM ANWAR UL HAQ")).isEqualTo("ANWAR UL HAQ");
        assertThat(DrivingLicenseExtractor.stripVehicleClasses("Motorcycle Light Vehicle JOHN DOE")).isEqualTo("JOHN DOE");
        assertThat(DrivingLicenseExtractor.stripVehicleClasses("JOHN DOE")).isEqualTo("JOHN DOE");
    }

    @Test
    void derivesDrivingExperienceFromTheIssueDate() {
        // "today" pinned: 18/09/1995 -> 15/10/2026 is 31 whole years (the motor quote's "Driving Experience")
        DrivingLicenseExtractor pinned = new DrivingLicenseExtractor(
                java.time.Clock.fixed(java.time.Instant.parse("2026-10-15T00:00:00Z"), java.time.ZoneOffset.UTC));
        ExtractionResult r = pinned.extract(text(SHARJAH_CARD, null));
        assertThat(r.fields()).containsEntry("driving_experience_years", 31);
        assertThat(r.fieldConfidence().get("driving_experience_years")).isEqualTo(0.95);   // same as the issue date

        // one day before the anniversary it is still 30
        DrivingLicenseExtractor dayBefore = new DrivingLicenseExtractor(
                java.time.Clock.fixed(java.time.Instant.parse("2026-09-17T00:00:00Z"), java.time.ZoneOffset.UTC));
        assertThat(dayBefore.extract(text(SHARJAH_CARD, null)).fields()).containsEntry("driving_experience_years", 30);

        // no issue date -> null; issue date in the future -> null plus a warning
        ExtractionResult none = pinned.extract(text("License No. 123456\nName: SOME ONE\nExpiry Date: 10/10/2030\n", null));
        assertThat(none.fields()).containsEntry("driving_experience_years", null);
        ExtractionResult future = pinned.extract(text("License No. 123456\nName: SOME ONE\nIssue Date: 10/10/2030\n", null));
        assertThat(future.fields()).containsEntry("driving_experience_years", null);
        assertThat(future.warnings()).contains("license_issue_date is in the future");
    }
}
