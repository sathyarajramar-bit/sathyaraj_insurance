package com.insurance.document.extraction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.insurance.document.entity.DocumentType;
import com.insurance.document.extraction.field.ExtractionResult;
import com.insurance.document.extraction.source.ExtractedText;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The envelope consumers already know: {@code message} / {@code messageType} ("S" success, "E" error) /
 * {@code response}. On success {@code response} holds the document's fields in snake_case followed by the
 * bookkeeping block ({@code field_confidence}, {@code warnings}, {@code source}, ...). On error it is null and
 * {@code error_code} says why.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractionResponse(String message, String messageType, String errorCode, Map<String, Object> response) {

    /** Value of {@code source} when the MOCK provider answered; the real sources are the {@link ExtractedText.Source} names. */
    public static final String SOURCE_MOCK = "MOCK";

    public static ExtractionResponse success(DocumentType documentType, Long documentId, ExtractedText text, ExtractionResult result) {
        Map<String, Object> body = new LinkedHashMap<>(result.fields());
        body.put("document_type", documentType.name());
        if (documentId != null) {
            body.put("document_id", documentId);
        }
        body.put("source", text.source().name());
        body.put("simulated", false);
        body.put("pages", text.pages().size());
        if (text.ocrConfidence() != null) {
            body.put("ocr_confidence", Math.round(text.ocrConfidence() * 100) / 100.0);
        }
        body.put("field_confidence", result.fieldConfidence());
        body.put("warnings", List.copyOf(result.warnings()));
        return new ExtractionResponse("success", "S", null, body);
    }

    /** Same shape as {@link #success}, but flagged: the values are fixtures, not a read of the file. */
    public static ExtractionResponse simulated(DocumentType documentType, Long documentId, ExtractionResult result) {
        Map<String, Object> body = new LinkedHashMap<>(result.fields());
        body.put("document_type", documentType.name());
        if (documentId != null) {
            body.put("document_id", documentId);
        }
        body.put("source", SOURCE_MOCK);
        body.put("simulated", true);
        body.put("pages", 1);
        body.put("field_confidence", result.fieldConfidence());
        body.put("warnings", List.copyOf(result.warnings()));
        return new ExtractionResponse("success", "S", null, body);
    }

    public static ExtractionResponse error(String code, String message) {
        return new ExtractionResponse(message, "E", code, null);
    }
}
