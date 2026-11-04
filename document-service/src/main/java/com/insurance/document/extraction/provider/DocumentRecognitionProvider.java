package com.insurance.document.extraction.provider;

import com.insurance.document.extraction.dto.ExtractionResponse;

/**
 * The port behind {@code POST /api/documents/extract}: bytes in, the S/E envelope out. Exactly one implementation
 * is active, chosen by {@code document.extraction.provider}:
 * <ul>
 *   <li>{@code LOCAL} ({@link LocalRecognitionProvider}, default): PDFBox text layer, Tesseract for scans, then the
 *       field extractors. Reads what is really in the file.</li>
 *   <li>{@code MOCK} ({@link MockRecognitionProvider}): deterministic sample data per document type and scenario for
 *       development and tests. Never reads the file beyond checking that it is a PDF.</li>
 * </ul>
 * Neither implementation calls anything outside this JVM.
 */
public interface DocumentRecognitionProvider {

    ExtractionResponse recognise(RecognitionRequest request);
}
