-- policy_db. A policy snapshots everything from the proposal at issuance; only numbers/ids point elsewhere.

CREATE TABLE policies (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_number             VARCHAR(30)   NOT NULL,
    status                    VARCHAR(20)   NOT NULL,   -- PENDING | ACTIVE | EXPIRED | CANCELLED
    user_id                   BIGINT        NOT NULL,
    customer_id               BIGINT        NOT NULL,
    proposal_number           VARCHAR(30)   NOT NULL,
    quote_number              VARCHAR(30)   NOT NULL,
    payment_reference         VARCHAR(30)   NOT NULL,
    product_id                BIGINT        NOT NULL,
    product_code              VARCHAR(40)   NOT NULL,
    product_name              VARCHAR(150)  NOT NULL,
    coverage_type             VARCHAR(20)   NOT NULL,
    term_months               INT           NOT NULL,
    vehicle_id                BIGINT        NOT NULL,
    registration_number       VARCHAR(20)   NOT NULL,
    idv                       DECIMAL(14,2) NOT NULL,
    premium_amount            DECIMAL(12,2) NOT NULL,
    holder_name               VARCHAR(200)  NOT NULL,
    holder_email              VARCHAR(255)  NOT NULL,
    nominee_name              VARCHAR(150),
    nominee_relationship      VARCHAR(20),
    start_date                DATE          NOT NULL,
    end_date                  DATE          NOT NULL,
    issued_at                 DATETIME(6)   NOT NULL,
    renewed_from_policy_number VARCHAR(30),
    renewed_by_policy_number  VARCHAR(30),
    cancelled_at              DATETIME(6),
    cancellation_reason       VARCHAR(500),
    schedule_document_id      BIGINT,
    created_at                DATETIME(6)   NOT NULL,
    created_by                VARCHAR(64),
    updated_at                DATETIME(6)   NOT NULL,
    updated_by                VARCHAR(64),
    version                   BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_policies_number UNIQUE (policy_number),
    CONSTRAINT uk_policies_payment UNIQUE (payment_reference),
    CONSTRAINT uk_policies_proposal UNIQUE (proposal_number)
);

CREATE INDEX idx_policies_user_created ON policies (user_id, created_at);
CREATE INDEX idx_policies_customer ON policies (customer_id);
CREATE INDEX idx_policies_status_end ON policies (status, end_date);   -- expiry job and renewal reminders
CREATE INDEX idx_policies_registration ON policies (registration_number);

-- Which reminder (30/15/7 days) was already sent for which policy: makes the reminder job idempotent.
CREATE TABLE policy_renewal_reminders (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_number VARCHAR(30) NOT NULL,
    days_before   INT         NOT NULL,
    sent_at       DATETIME(6) NOT NULL,
    CONSTRAINT uk_policy_reminders UNIQUE (policy_number, days_before)
);
