-- payment_db. One row per payment attempt; the idempotency key makes client retries safe.

CREATE TABLE payments (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_reference  VARCHAR(30)   NOT NULL,
    idempotency_key    VARCHAR(100)  NOT NULL,
    status             VARCHAR(20)   NOT NULL,   -- INITIATED | SUCCESS | FAILED | REFUNDED
    purpose            VARCHAR(20)   NOT NULL,   -- NEW_POLICY | RENEWAL
    user_id            BIGINT        NOT NULL,
    customer_id        BIGINT        NOT NULL,
    proposal_number    VARCHAR(30),
    policy_number      VARCHAR(30),
    amount             DECIMAL(12,2) NOT NULL,
    currency           VARCHAR(3)    NOT NULL,
    payment_method     VARCHAR(20)   NOT NULL,   -- CARD | UPI | NET_BANKING
    provider           VARCHAR(30)   NOT NULL,
    provider_txn_id    VARCHAR(64),
    failure_reason     VARCHAR(255),
    refund_reference   VARCHAR(64),
    refunded_at        DATETIME(6),
    refund_reason      VARCHAR(255),
    completed_at       DATETIME(6),
    created_at         DATETIME(6)   NOT NULL,
    created_by         VARCHAR(64),
    updated_at         DATETIME(6)   NOT NULL,
    updated_by         VARCHAR(64),
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_payments_reference UNIQUE (payment_reference),
    CONSTRAINT uk_payments_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_payments_user_created ON payments (user_id, created_at);
CREATE INDEX idx_payments_proposal ON payments (proposal_number, status);
CREATE INDEX idx_payments_policy ON payments (policy_number, status);

-- Transactional outbox: the PaymentSuccessful event is committed together with the payment row, so a
-- crash between "charged" and "told policy-service" can never lose the event.
CREATE TABLE payment_outbox (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_reference VARCHAR(30)  NOT NULL,
    event_type        VARCHAR(40)  NOT NULL,
    payload           TEXT         NOT NULL,
    status            VARCHAR(20)  NOT NULL,   -- PENDING | DELIVERED | FAILED
    attempts          INT          NOT NULL DEFAULT 0,
    last_error        VARCHAR(500),
    next_attempt_at   DATETIME(6)  NOT NULL,
    delivered_at      DATETIME(6),
    created_at        DATETIME(6)  NOT NULL,
    CONSTRAINT uk_payment_outbox_event UNIQUE (payment_reference, event_type)
);

CREATE INDEX idx_payment_outbox_pending ON payment_outbox (status, next_attempt_at);
