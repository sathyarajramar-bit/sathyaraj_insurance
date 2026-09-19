package com.insurance.document.repository;

import com.insurance.document.entity.Document;
import com.insurance.document.entity.DocumentStatus;
import com.insurance.document.entity.ReferenceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Page<Document> findByReferenceTypeAndReferenceNumberAndStatusNot(ReferenceType type, String number, DocumentStatus excluded, Pageable pageable);

    Page<Document> findByReferenceTypeAndReferenceNumberAndOwnerUserIdAndStatusNot(ReferenceType type, String number, Long ownerUserId,
                                                                                   DocumentStatus excluded, Pageable pageable);

    Page<Document> findByOwnerUserIdAndStatusNot(Long ownerUserId, DocumentStatus excluded, Pageable pageable);
}
