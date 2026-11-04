package com.insurance.document.config;

import com.insurance.document.extraction.provider.MockScenario;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "document")
public class DocumentProperties {

    private Storage storage = new Storage();

    /** Content types accepted for uploads. */
    private List<String> allowedContentTypes = List.of("application/pdf", "image/jpeg", "image/png", "text/plain");

    private long maxSizeBytes = 10L * 1024 * 1024;

    private Extraction extraction = new Extraction();

    @Getter
    @Setter
    public static class Storage {
        private String provider = "LOCAL";
        private String localDir = "./data/documents";
    }

    /** {@code document.extraction.*}: turning uploaded PDFs / images into structured JSON. */
    @Getter
    @Setter
    public static class Extraction {
        /** Which {@code DocumentRecognitionProvider} answers: LOCAL (PDFBox + Tesseract) or MOCK (fixtures). */
        private Provider provider = Provider.LOCAL;
        /** A PDF whose text layer has fewer characters than this is treated as a scan and goes through OCR. */
        private int minTextLayerChars = 40;
        private Ocr ocr = new Ocr();
        private Mock mock = new Mock();

        public enum Provider { LOCAL, MOCK }

        @Getter
        @Setter
        public static class Ocr {
            /** Master switch. Off: scans and images are rejected with messageType E instead of being OCR'd. */
            private boolean enabled = true;
            /** Folder holding {@code eng.traineddata}, {@code ara.traineddata}, ... (Tesseract tessdata). */
            private String tessdataPath = "./data/tessdata";
            /** Tesseract language stack. UAE documents carry both scripts, so both models are loaded. */
            private String languages = "eng+ara";
            /** Resolution used when a scanned PDF page is rendered before OCR. 300 is Tesseract's sweet spot. */
            private int renderDpi = 300;
            /** Second OCR pass that yields per-word confidences (doubles OCR time; worth it for review queues). */
            private boolean confidence = true;
        }

        /** {@code document.extraction.mock.*}: only read when {@code provider=MOCK}. */
        @Getter
        @Setter
        public static class Mock {
            /** Scenario used when the request carries no {@code ?scenario=}. */
            private MockScenario defaultScenario = MockScenario.SUCCESS;
            /** When false the request parameter is ignored and every call gets the default scenario. */
            private boolean allowScenarioParam = true;
        }
    }
}
