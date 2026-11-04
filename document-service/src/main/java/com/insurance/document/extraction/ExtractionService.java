package com.insurance.document.extraction;

import com.insurance.common.exception.ValidationException;
import com.insurance.document.config.DocumentProperties;
import com.insurance.document.entity.DocumentType;
import com.insurance.document.extraction.dto.ExtractionResponse;
import com.insurance.document.extraction.provider.DocumentRecognitionProvider;
import com.insurance.document.extraction.provider.RecognitionRequest;
import com.insurance.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Validates the input, fetches stored bytes when needed and hands the work to the active
 * {@link DocumentRecognitionProvider} (LOCAL pipeline or MOCK fixtures, see {@code document.extraction.provider}).
 *
 * <p>Nothing is persisted: the caller (proposal/claim UI) decides what to do with the fields, typically prefill a
 * form and let an agent confirm. Storing the JSON next to the document is a one-column addition when needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionService {

    private final DocumentService documentService;
    private final DocumentProperties properties;
    private final DocumentRecognitionProvider provider;

    /** Extracts from a document already stored by {@code POST /api/documents}; same read rules as a download. */
    public ExtractionResponse extractStored(Long documentId, DocumentType overrideType, String scenario) {
        DocumentService.Download download = documentService.download(documentId);
        byte[] bytes;
        try (InputStream in = download.content()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new ExtractionException("STORAGE_READ_FAILED", "Stored content could not be read: " + e.getMessage(), e);
        }
        DocumentType type = overrideType != null ? overrideType : documentService.get(documentId).documentType();
        return provider.recognise(new RecognitionRequest(bytes, download.contentType(), type, documentId, scenario));
    }

    /** Extracts from an uploaded file without storing it: for previews and for callers that keep their own copy. */
    public ExtractionResponse extractUpload(byte[] content, String contentType, DocumentType type, String scenario) {
        if (content == null || content.length == 0) {
            throw new ValidationException("File is empty");
        }
        if (content.length > properties.getMaxSizeBytes()) {
            throw new ValidationException("File exceeds the maximum size of " + properties.getMaxSizeBytes() + " bytes");
        }
        if (contentType == null || properties.getAllowedContentTypes().stream().noneMatch(contentType::equalsIgnoreCase)) {
            throw new ValidationException("Content type " + contentType + " is not allowed (" + properties.getAllowedContentTypes() + ")");
        }
        return provider.recognise(new RecognitionRequest(content, contentType, type, null, scenario));
    }
}
