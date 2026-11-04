package com.insurance.document.extraction.provider;

import com.insurance.common.exception.ValidationException;
import com.insurance.document.config.DocumentProperties;
import com.insurance.document.extraction.ExtractionException;
import com.insurance.document.extraction.dto.ExtractionResponse;
import com.insurance.document.extraction.field.ExtractionResult;
import com.insurance.document.extraction.source.ContentKind;
import com.insurance.document.extraction.source.ContentKindDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Stand-in for an OCR service while developing or testing offline. Checks that the upload really is a PDF (magic
 * bytes, not the declared type), then answers with the fixture for the document type and the requested scenario.
 *
 * <p>This is <b>not</b> document recognition: the same PDF and the same scenario always give the same values, and a
 * different PDF gives them too. The envelope says so ({@code source=MOCK}, {@code simulated=true} and a warning) so
 * nobody mistakes the output for a verified read. Switch on with {@code document.extraction.provider=MOCK}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "document.extraction.provider", havingValue = "MOCK")
public class MockRecognitionProvider implements DocumentRecognitionProvider {

    private final DocumentProperties.Extraction.Mock settings;
    private final ContentKindDetector detector;
    private final Clock clock;

    public MockRecognitionProvider(DocumentProperties properties, ContentKindDetector detector, Clock clock) {
        this.settings = properties.getExtraction().getMock();
        this.detector = detector;
        this.clock = clock;
        log.warn("Document extraction runs the MOCK provider: responses are simulated sample data, default scenario {}",
                settings.getDefaultScenario());
    }

    @Override
    public ExtractionResponse recognise(RecognitionRequest request) {
        if (detector.detect(request.content(), request.contentType()) != ContentKind.PDF) {
            throw new ValidationException("The mock extraction provider accepts PDF files only");
        }
        MockScenario scenario = resolve(request.scenario());
        // Only sizes and ids are logged, never the content.
        log.info("Simulated extraction: type={} scenario={} bytes={} document={}", request.type(), scenario,
                request.content().length, request.documentId() == null ? "upload" : request.documentId());
        if (scenario == MockScenario.PROCESSING_FAILURE) {
            throw new ExtractionException("OCR_FAILED", "Simulated processing failure (scenario PROCESSING_FAILURE)");
        }
        ExtractionResult result = MockFixtures.build(request.type(), scenario, clock);
        result.warn(MockFixtures.SIMULATED_WARNING);
        return ExtractionResponse.simulated(request.type(), request.documentId(), result);
    }

    private MockScenario resolve(String requested) {
        if (!settings.isAllowScenarioParam()) {
            return settings.getDefaultScenario();
        }
        return MockScenario.from(requested, settings.getDefaultScenario());
    }
}
