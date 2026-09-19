-- document_db: metadata only. Bytes live in the storage provider under storage_key.

CREATE TABLE documents (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id    BIGINT       NOT NULL,
    customer_id      BIGINT,
    reference_type   VARCHAR(20)  NOT NULL,   -- POLICY | CLAIM | CUSTOMER | PROPOSAL
    reference_number VARCHAR(40)  NOT NULL,
    document_type    VARCHAR(30)  NOT NULL,   -- POLICY_SCHEDULE | CLAIM_PHOTO | FIR | REPAIR_ESTIMATE | ID_PROOF | ADDRESS_PROOF | OTHER
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    checksum_sha256  VARCHAR(64)  NOT NULL,
    storage_provider VARCHAR(20)  NOT NULL,   -- LOCAL | S3
    storage_key      VARCHAR(255) NOT NULL,
    status           VARCHAR(20)  NOT NULL,   -- UPLOADED | VERIFIED | REJECTED | DELETED
    status_reason    VARCHAR(255),
    created_at       DATETIME(6)  NOT NULL,
    created_by       VARCHAR(64),
    updated_at       DATETIME(6)  NOT NULL,
    updated_by       VARCHAR(64),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_documents_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_documents_reference ON documents (reference_type, reference_number);
CREATE INDEX idx_documents_owner ON documents (owner_user_id, created_at);
