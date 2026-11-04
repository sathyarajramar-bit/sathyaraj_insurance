package com.insurance.document.extraction.provider;

import com.insurance.common.exception.ValidationException;
import com.insurance.document.config.DocumentProperties;
import com.insurance.document.entity.DocumentType;
import com.insurance.document.extraction.ExtractionException;
import com.insurance.document.extraction.dto.ExtractionResponse;
import com.insurance.document.extraction.source.ContentKindDetector;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** No Spring: the provider is pure logic over properties, the byte sniffer and a clock. */
class MockRecognitionProviderTest {

    static final byte[] PDF = "%PDF-1.4\n1 0 obj << >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0};
    static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC);

    private static MockRecognitionProvider provider(MockScenario defaultScenario, boolean allowParam) {
        DocumentProperties props = new DocumentProperties();
        props.getExtraction().setProvider(DocumentProperties.Extraction.Provider.MOCK);
        props.getExtraction().getMock().setDefaultScenario(defaultScenario);
        props.getExtraction().getMock().setAllowScenarioParam(allowParam);
        return new MockRecognitionProvider(props, new ContentKindDetector(), FIXED);
    }

    private static RecognitionRequest request(byte[] bytes, String contentType, DocumentType type, String scenario) {
        return new RecognitionRequest(bytes, contentType, type, null, scenario);
    }

    @SuppressWarnings("unchecked")
    private static List<String> warnings(Map<String, Object> body) {
        return (List<String>) body.get("warnings");
    }

    @Test
    void sameInputAlwaysGivesTheSameSimulatedLicence() {
        MockRecognitionProvider p = provider(MockScenario.SUCCESS, true);
        ExtractionResponse first = p.recognise(request(PDF, "application/pdf", DocumentType.DRIVING_LICENSE, null));
        ExtractionResponse second = p.recognise(request("%PDF-1.7 something else".getBytes(StandardCharsets.US_ASCII),
                "application/pdf", DocumentType.DRIVING_LICENSE, "success"));

        assertThat(first).isEqualTo(second);
        assertThat(first.messageType()).isEqualTo("S");
        Map<String, Object> body = first.response();
        assertThat(body).containsEntry("license_number", "0194428")
                .containsEntry("license_issue_date", "18/09/2015")
                .containsEntry("driving_experience_years", 11)     // 18/09/2015 -> 15/10/2026 on the fixed clock
                .containsEntry("traffic_code", "3070265345")
                .containsEntry("document_type", "DRIVING_LICENSE")
                .containsEntry("source", "MOCK")
                .containsEntry("simulated", true);
        assertThat(warnings(body)).contains(MockFixtures.SIMULATED_WARNING);
        assertThat(body).doesNotContainKey("document_id");
    }

    @Test
    void missingFieldsScenarioNullsTheRequiredFieldsAndWarns() {
        ExtractionResponse r = provider(MockScenario.SUCCESS, true)
                .recognise(request(PDF, "application/pdf", DocumentType.DRIVING_LICENSE, "MISSING_FIELDS"));

        assertThat(r.response()).containsEntry("license_issue_date", null)
                .containsEntry("traffic_code", null)
                .containsEntry("driving_experience_years", null)
                .containsEntry("license_number", "0194428");
        assertThat(warnings(r.response()))
                .contains("Required field missing: license_issue_date", "Required field missing: traffic_code");
        @SuppressWarnings("unchecked")
        Map<String, Double> conf = (Map<String, Double>) r.response().get("field_confidence");
        assertThat(conf).doesNotContainKey("traffic_code").containsKey("license_number");
    }

    @Test
    void processingFailureScenarioFailsLikeABrokenOcrEngine() {
        assertThatThrownBy(() -> provider(MockScenario.SUCCESS, true)
                .recognise(request(PDF, "application/pdf", DocumentType.VEHICLE_REGISTRATION, "PROCESSING_FAILURE")))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("Simulated processing failure")
                .extracting("code").isEqualTo("OCR_FAILED");
    }

    @Test
    void defaultScenarioAppliesWhenNoneIsRequestedOrTheParamIsDisabled() {
        assertThatThrownBy(() -> provider(MockScenario.PROCESSING_FAILURE, true)
                .recognise(request(PDF, "application/pdf", DocumentType.OTHER, null)))
                .isInstanceOf(ExtractionException.class);

        // param disabled: an explicit SUCCESS is ignored and the configured failure still applies
        assertThatThrownBy(() -> provider(MockScenario.PROCESSING_FAILURE, false)
                .recognise(request(PDF, "application/pdf", DocumentType.OTHER, "SUCCESS")))
                .isInstanceOf(ExtractionException.class);
    }

    @Test
    void rejectsNonPdfBytesAndUnknownScenarios() {
        MockRecognitionProvider p = provider(MockScenario.SUCCESS, true);
        // declared as PDF, really a PNG
        assertThatThrownBy(() -> p.recognise(request(PNG, "application/pdf", DocumentType.OTHER, null)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("PDF files only");
        assertThatThrownBy(() -> p.recognise(request(PDF, "application/pdf", DocumentType.OTHER, "EXPLODE")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Unknown scenario");
    }

    @Test
    void everyDocumentTypeHasAFixtureWithTheReferenceKeys() {
        MockRecognitionProvider p = provider(MockScenario.SUCCESS, true);
        assertThat(p.recognise(request(PDF, "application/pdf", DocumentType.VEHICLE_REGISTRATION, null)).response())
                .containsKeys("traffic_plate_number", "chassis_number", "model_year", "engine_number", "vehicle_registered");
        assertThat(p.recognise(request(PDF, "application/pdf", DocumentType.EMIRATES_ID, null)).response())
                .containsKeys("emirate_id", "name_english", "date_of_birth", "gender", "card_number");
        assertThat(p.recognise(request(PDF, "application/pdf", DocumentType.OTHER, null)).response())
                .containsKeys("key_values", "lines", "raw_text");
        assertThat(p.recognise(request(PDF, "application/pdf", DocumentType.CLAIM_PHOTO, null)).response())
                .containsEntry("document_type", "CLAIM_PHOTO").containsKey("key_values");
    }
}
