-- notification_db: one row per message per channel, with delivery outcome.

CREATE TABLE notifications (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key  VARCHAR(150) NOT NULL,
    event_type       VARCHAR(40)  NOT NULL,
    channel          VARCHAR(10)  NOT NULL,   -- EMAIL | SMS
    user_id          BIGINT,
    customer_id      BIGINT,
    recipient        VARCHAR(255) NOT NULL,
    reference_type   VARCHAR(20),
    reference_number VARCHAR(40),
    subject          VARCHAR(255),
    body             TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL,   -- PENDING | SENT | FAILED
    attempts         INT          NOT NULL DEFAULT 0,
    last_error       VARCHAR(500),
    provider         VARCHAR(30),
    provider_message_id VARCHAR(100),
    sent_at          DATETIME(6),
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    CONSTRAINT uk_notifications_key_channel UNIQUE (idempotency_key, channel)
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at);
CREATE INDEX idx_notifications_status ON notifications (status, updated_at);
CREATE INDEX idx_notifications_reference ON notifications (reference_type, reference_number);
