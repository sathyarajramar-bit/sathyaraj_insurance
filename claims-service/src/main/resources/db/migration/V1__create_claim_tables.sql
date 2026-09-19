-- claims_db. Policy facts are snapshotted at registration; documents are referenced by document-service id.

CREATE TABLE claims (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_number           VARCHAR(30)   NOT NULL,
    status                 VARCHAR(25)   NOT NULL,   -- REGISTERED | UNDER_REVIEW | DOCUMENTS_REQUIRED | APPROVED | REJECTED | SETTLED | CLOSED
    user_id                BIGINT        NOT NULL,
    customer_id            BIGINT        NOT NULL,
    policy_number          VARCHAR(30)   NOT NULL,
    product_code           VARCHAR(40)   NOT NULL,
    registration_number    VARCHAR(20)   NOT NULL,
    idv                    DECIMAL(14,2) NOT NULL,
    claim_type             VARCHAR(20)   NOT NULL,   -- ACCIDENT | THEFT | FIRE | NATURAL_CALAMITY | THIRD_PARTY
    incident_date          DATE          NOT NULL,
    incident_location      VARCHAR(255)  NOT NULL,
    description            VARCHAR(2000) NOT NULL,
    claimed_amount         DECIMAL(12,2) NOT NULL,
    assessed_amount        DECIMAL(12,2),
    approved_amount        DECIMAL(12,2),
    settled_amount         DECIMAL(12,2),
    settlement_reference   VARCHAR(64),
    assessor               VARCHAR(64),
    assessment_notes       VARCHAR(2000),
    decision_reason        VARCHAR(500),
    documents_requested    VARCHAR(500),
    documents_requested_at DATETIME(6),
    settled_at             DATETIME(6),
    closed_at              DATETIME(6),
    reopened_count         INT           NOT NULL DEFAULT 0,
    created_at             DATETIME(6)   NOT NULL,
    created_by             VARCHAR(64),
    updated_at             DATETIME(6)   NOT NULL,
    updated_by             VARCHAR(64),
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_claims_number UNIQUE (claim_number)
);

CREATE INDEX idx_claims_user_created ON claims (user_id, created_at);
CREATE INDEX idx_claims_policy ON claims (policy_number);
CREATE INDEX idx_claims_status ON claims (status, documents_requested_at);

CREATE TABLE claim_documents (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_id      BIGINT       NOT NULL,
    document_id   BIGINT       NOT NULL,
    document_type VARCHAR(30)  NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    attached_by   VARCHAR(64)  NOT NULL,
    attached_at   DATETIME(6)  NOT NULL,
    CONSTRAINT uk_claim_documents UNIQUE (claim_id, document_id),
    CONSTRAINT fk_claim_documents_claim FOREIGN KEY (claim_id) REFERENCES claims (id) ON DELETE CASCADE
);

CREATE TABLE claim_status_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_id    BIGINT       NOT NULL,
    from_status VARCHAR(25),
    to_status   VARCHAR(25)  NOT NULL,
    changed_by  VARCHAR(64)  NOT NULL,
    reason      VARCHAR(500),
    changed_at  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_claim_history_claim FOREIGN KEY (claim_id) REFERENCES claims (id) ON DELETE CASCADE
);

CREATE INDEX idx_claim_history_claim ON claim_status_history (claim_id, changed_at);
