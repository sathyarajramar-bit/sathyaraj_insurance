-- proposal_db. One proposal per quote. Quote/customer facts are snapshotted; only ids/numbers reference other services.

CREATE TABLE proposals (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    proposal_number       VARCHAR(30)   NOT NULL,
    status                VARCHAR(20)   NOT NULL,   -- DRAFT | SUBMITTED | UNDER_REVIEW | APPROVED | REJECTED
    user_id               BIGINT        NOT NULL,
    customer_id           BIGINT        NOT NULL,
    quote_number          VARCHAR(30)   NOT NULL,
    quote_id              BIGINT        NOT NULL,
    product_id            BIGINT        NOT NULL,
    product_code          VARCHAR(40)   NOT NULL,
    product_name          VARCHAR(150)  NOT NULL,
    coverage_type         VARCHAR(20)   NOT NULL,
    term_months           INT           NOT NULL,
    vehicle_id            BIGINT        NOT NULL,
    registration_number   VARCHAR(20)   NOT NULL,
    idv                   DECIMAL(14,2) NOT NULL,
    premium_amount        DECIMAL(12,2) NOT NULL,   -- final premium of the quote: what payment-service charges
    proposer_first_name   VARCHAR(100)  NOT NULL,
    proposer_last_name    VARCHAR(100)  NOT NULL,
    proposer_email        VARCHAR(255)  NOT NULL,
    proposer_phone        VARCHAR(20),
    proposer_dob          DATE,
    address_line1         VARCHAR(255),
    address_line2         VARCHAR(255),
    city                  VARCHAR(100),
    state                 VARCHAR(100),
    postal_code           VARCHAR(20),
    country               VARCHAR(100),
    nominee_name          VARCHAR(150),
    nominee_relationship  VARCHAR(20),
    nominee_dob           DATE,
    declarations_accepted BOOLEAN       NOT NULL DEFAULT FALSE,
    submitted_at          DATETIME(6),
    reviewed_by           VARCHAR(64),
    reviewed_at           DATETIME(6),
    decision_reason       VARCHAR(500),
    created_at            DATETIME(6)   NOT NULL,
    created_by            VARCHAR(64),
    updated_at            DATETIME(6)   NOT NULL,
    updated_by            VARCHAR(64),
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_proposals_number UNIQUE (proposal_number),
    CONSTRAINT uk_proposals_quote UNIQUE (quote_number)
);

CREATE INDEX idx_proposals_user_created ON proposals (user_id, created_at);
CREATE INDEX idx_proposals_customer ON proposals (customer_id);
CREATE INDEX idx_proposals_status_submitted ON proposals (status, submitted_at);   -- review work queue

-- Audit trail of every transition: who moved it, from what, to what, why.
CREATE TABLE proposal_status_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    proposal_id BIGINT       NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    changed_by  VARCHAR(64)  NOT NULL,
    reason      VARCHAR(500),
    changed_at  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_proposal_history_proposal FOREIGN KEY (proposal_id) REFERENCES proposals (id) ON DELETE CASCADE
);

CREATE INDEX idx_proposal_history_proposal ON proposal_status_history (proposal_id, changed_at);
