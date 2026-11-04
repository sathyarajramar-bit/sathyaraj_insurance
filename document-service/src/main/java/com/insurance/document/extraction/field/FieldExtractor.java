package com.insurance.document.extraction.field;

import com.insurance.document.entity.DocumentType;
import com.insurance.document.extraction.source.ExtractedText;

/**
 * Stage two of the pipeline: text in, typed fields out. One implementation per document type; the file format
 * (PDF, scan, photo) is already irrelevant here. Register a new type by adding a bean, nothing else changes.
 */
public interface FieldExtractor {

    /** The document type this extractor understands. */
    DocumentType supports();

    /** Builds the {@code response} payload of the extraction envelope. */
    ExtractionResult extract(ExtractedText text);
}
