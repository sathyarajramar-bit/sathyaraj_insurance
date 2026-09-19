package com.insurance.document.service;

import com.insurance.common.dto.PageResponse;
import com.insurance.common.exception.ForbiddenException;
import com.insurance.common.exception.ValidationException;
import com.insurance.common.security.AuthenticatedUser;
import com.insurance.common.security.CurrentUser;
import com.insurance.document.config.DocumentProperties;
import com.insurance.document.dto.DocumentResponse;
import com.insurance.document.dto.DocumentStatusRequest;
import com.insurance.document.dto.GeneratedDocumentRequest;
import com.insurance.document.entity.Document;
import com.insurance.document.entity.DocumentStatus;
import com.insurance.document.entity.DocumentType;
import com.insurance.document.entity.ReferenceType;
import com.insurance.document.exception.DocumentNotFoundException;
import com.insurance.document.mapper.DocumentMapper;
import com.insurance.document.repository.DocumentRepository;
import com.insurance.document.storage.DocumentStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Bytes go to storage first, metadata second; if the insert fails the orphan file is removed.
 * Reads check ownership: owner, ADMIN/AGENT/CLAIMS_HANDLER, or SERVICE (other services).
 * Deletion is soft (status DELETED) so references from claims/policies stay resolvable in audits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository repository;
    private final DocumentStorage storage;
    private final DocumentMapper mapper;
    private final DocumentProperties properties;

    public DocumentResponse upload(ReferenceType referenceType, String referenceNumber, DocumentType documentType,
                                   String fileName, String contentType, byte[] content) {
        return store(CurrentUser.require().userId(), null, referenceType, referenceNumber, documentType, fileName, contentType, content);
    }

    public DocumentResponse storeGenerated(GeneratedDocumentRequest request) {
        byte[] content;
        try {
            content = Base64.getDecoder().decode(request.contentBase64());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("contentBase64 is not valid base64");
        }
        return store(request.userId(), request.customerId(), request.referenceType(), request.referenceNumber(),
                request.documentType(), request.fileName(), request.contentType(), content);
    }

    private DocumentResponse store(Long ownerUserId, Long customerId, ReferenceType referenceType, String referenceNumber,
                                   DocumentType documentType, String fileName, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new ValidationException("File is empty");
        }
        if (content.length > properties.getMaxSizeBytes()) {
            throw new ValidationException("File exceeds the maximum size of " + properties.getMaxSizeBytes() + " bytes");
        }
        if (contentType == null || properties.getAllowedContentTypes().stream().noneMatch(contentType::equalsIgnoreCase)) {
            throw new ValidationException("Content type " + contentType + " is not allowed (" + properties.getAllowedContentTypes() + ")");
        }
        String safeName = fileName == null ? "document" : fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        LocalDate today = LocalDate.now();
        String key = today.getYear() + "/" + String.format("%02d", today.getMonthValue()) + "/" + UUID.randomUUID() + "-" + safeName;
        String storedKey = storage.store(key, content);
        try {
            Document saved = repository.save(Document.builder()
                    .ownerUserId(ownerUserId).customerId(customerId)
                    .referenceType(referenceType).referenceNumber(referenceNumber).documentType(documentType)
                    .fileName(safeName).contentType(contentType).sizeBytes(content.length).checksumSha256(sha256(content))
                    .storageProvider(storage.provider()).storageKey(storedKey).status(DocumentStatus.UPLOADED)
                    .build());
            log.info("Document {} ({} {} {}) stored as {}", saved.getId(), referenceType, referenceNumber, documentType, storedKey);
            return mapper.toResponse(saved);
        } catch (RuntimeException e) {
            storage.delete(storedKey);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(Long id) {
        return mapper.toResponse(findReadable(id));
    }

    @Transactional(readOnly = true)
    public Download download(Long id) {
        Document doc = findReadable(id);
        if (doc.getStatus() == DocumentStatus.DELETED) {
            throw new DocumentNotFoundException(id);
        }
        return new Download(doc.getFileName(), doc.getContentType(), doc.getSizeBytes(), storage.read(doc.getStorageKey()));
    }

    /** Staff and services see every document of the reference; a customer sees only the ones they uploaded. */
    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> listByReference(ReferenceType type, String number, Pageable pageable) {
        AuthenticatedUser user = CurrentUser.require();
        if (user.hasAnyRole("ADMIN", "AGENT", "CLAIMS_HANDLER", "SERVICE")) {
            return PageResponse.from(repository.findByReferenceTypeAndReferenceNumberAndStatusNot(type, number, DocumentStatus.DELETED, pageable), mapper::toResponse);
        }
        return PageResponse.from(repository.findByReferenceTypeAndReferenceNumberAndOwnerUserIdAndStatusNot(type, number, user.userId(),
                DocumentStatus.DELETED, pageable), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> listMine(Pageable pageable) {
        return PageResponse.from(repository.findByOwnerUserIdAndStatusNot(CurrentUser.require().userId(), DocumentStatus.DELETED, pageable), mapper::toResponse);
    }

    @Transactional
    public DocumentResponse updateStatus(Long id, DocumentStatusRequest request) {
        if (request.status() != DocumentStatus.VERIFIED && request.status() != DocumentStatus.REJECTED) {
            throw new ValidationException("Status must be VERIFIED or REJECTED");
        }
        if (request.status() == DocumentStatus.REJECTED && (request.reason() == null || request.reason().isBlank())) {
            throw new ValidationException("A reason is required to reject a document");
        }
        Document doc = repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
        doc.setStatus(request.status());
        doc.setStatusReason(request.reason());
        return mapper.toResponse(doc);
    }

    @Transactional
    public void delete(Long id) {
        Document doc = findReadable(id);
        AuthenticatedUser user = CurrentUser.require();
        if (!doc.isOwnedBy(user.userId()) && !user.hasRole("ADMIN")) {
            throw new ForbiddenException("Only the owner or an ADMIN can delete a document");
        }
        doc.setStatus(DocumentStatus.DELETED);
        storage.delete(doc.getStorageKey());
    }

    private Document findReadable(Long id) {
        Document doc = repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
        AuthenticatedUser user = CurrentUser.require();
        if (!doc.isOwnedBy(user.userId()) && !user.hasAnyRole("ADMIN", "AGENT", "CLAIMS_HANDLER", "SERVICE")) {
            throw new ForbiddenException("You are not allowed to access this document");
        }
        return doc;
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Download(String fileName, String contentType, long sizeBytes, InputStream content) {
    }
}
