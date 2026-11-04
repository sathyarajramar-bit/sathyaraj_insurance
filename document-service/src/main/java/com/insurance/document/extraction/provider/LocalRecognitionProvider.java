package com.insurance.document.extraction.provider;

import com.insurance.document.entity.DocumentType;
import com.insurance.document.extraction.ExtractionException;
import com.insurance.document.extraction.dto.ExtractionResponse;
import com.insurance.document.extraction.field.ExtractionResult;
import com.insurance.document.extraction.field.FieldExtractor;
import com.insurance.document.extraction.source.ExtractedText;
import com.insurance.document.extraction.source.TextSourceRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The real pipeline, all in-process: {@link TextSourceRouter} (bytes -> text via PDFBox or Tesseract) then the
 * {@link FieldExtractor} registered for the document type (text -> fields). Types without their own extractor fall
 * back to the generic one. Active unless {@code document.extraction.provider} says otherwise.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "document.extraction.provider", havingValue = "LOCAL", matchIfMissing = true)
public class LocalRecognitionProvider implements DocumentRecognitionProvider {

    private final TextSourceRouter textSource;
    private final Map<DocumentType, FieldExtractor> extractors;

    public LocalRecognitionProvider(TextSourceRouter textSource, List<FieldExtractor> extractors) {
        this.textSource = textSource;
        this.extractors = extractors.stream().collect(Collectors.toMap(FieldExtractor::supports, Function.identity()));
        log.info("Field extractors registered for {}", this.extractors.keySet());
    }

    @Override
    public ExtractionResponse recognise(RecognitionRequest request) {
        long started = System.nanoTime();
        ExtractedText text = textSource.extract(request.content(), request.contentType());
        if (text.isBlank()) {
            throw new ExtractionException("NO_TEXT", "No text could be read from the document (blank page or unreadable scan)");
        }
        FieldExtractor extractor = extractors.getOrDefault(request.type(), extractors.get(DocumentType.OTHER));
        ExtractionResult result = extractor.extract(text);
        log.info("Extracted {} from document {} via {} in {} ms ({} fields, {} warnings)", request.type(),
                request.documentId() == null ? "upload" : request.documentId(), text.source(),
                (System.nanoTime() - started) / 1_000_000, result.fieldConfidence().size(), result.warnings().size());
        return ExtractionResponse.success(request.type(), request.documentId(), text, result);
    }
}
