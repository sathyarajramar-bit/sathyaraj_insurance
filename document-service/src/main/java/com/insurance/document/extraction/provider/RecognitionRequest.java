package com.insurance.document.extraction.provider;

import com.insurance.document.entity.DocumentType;

/**
 * Everything a {@link DocumentRecognitionProvider} needs for one call.
 *
 * @param content     the file bytes, already validated for emptiness, size and declared content type
 * @param contentType the declared content type (the providers re-check the real bytes)
 * @param type        which document the caller says this is; drives the field extractor or the fixture
 * @param documentId  id of the stored document, or null for a preview upload that is not stored
 * @param scenario    optional test scenario name; only the mock provider looks at it
 */
public record RecognitionRequest(byte[] content, String contentType, DocumentType type, Long documentId, String scenario) {
}
