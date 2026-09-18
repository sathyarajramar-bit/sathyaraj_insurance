-- quote_db. A quote is a priced SNAPSHOT: product, pricing inputs and vehicle facts are copied in so the
-- premium can be explained later even if the product or vehicle changes. Only ids reference other services.

CREATE TABLE quotes (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    quote_number          VARCHAR(30)   NOT NULL,
    status                VARCHAR(20)   NOT NULL,   -- GENERATED | ACCEPTED | EXPIRED | CANCELLED
    user_id               BIGINT        NOT NULL,   -- auth user who owns the quote
    customer_id           BIGINT        NOT NULL,   -- customer-service id
    product_id            BIGINT        NOT NULL,
    product_code          VARCHAR(40)   NOT NULL,
    product_name          VARCHAR(150)  NOT NULL,
    coverage_type         VARCHAR(20)   NOT NULL,
    term_months           INT           NOT NULL,
    vehicle_id            BIGINT        NOT NULL,
    registration_number   VARCHAR(20)   NOT NULL,
    vehicle_type          VARCHAR(10)   NOT NULL,
    make                  VARCHAR(50)   NOT NULL,
    model                 VARCHAR(50)   NOT NULL,
    fuel_type             VARCHAR(20)   NOT NULL,
    manufacturing_year    INT           NOT NULL,
    engine_capacity_cc    INT           NOT NULL,
    idv                   DECIMAL(14,2) NOT NULL,
    driver_age            INT           NOT NULL,
    ncb_percent           DECIMAL(6,2)  NOT NULL,
    own_damage_premium    DECIMAL(12,2) NOT NULL,
    third_party_premium   DECIMAL(12,2) NOT NULL,
    base_premium          DECIMAL(12,2) NOT NULL,
    add_on_premium        DECIMAL(12,2) NOT NULL,
    discount_amount       DECIMAL(12,2) NOT NULL,
    tax_amount            DECIMAL(12,2) NOT NULL,
    final_premium         DECIMAL(12,2) NOT NULL,
    valid_until           DATETIME(6)   NOT NULL,
    accepted_at           DATETIME(6),
    cancelled_at          DATETIME(6),
    created_at            DATETIME(6)   NOT NULL,
    created_by            VARCHAR(64),
    updated_at            DATETIME(6)   NOT NULL,
    updated_by            VARCHAR(64),
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_quotes_number UNIQUE (quote_number)
);

-- "my quotes" listing, admin search by customer, and the expiry job (status + valid_until).
CREATE INDEX idx_quotes_user_created ON quotes (user_id, created_at);
CREATE INDEX idx_quotes_customer ON quotes (customer_id);
CREATE INDEX idx_quotes_status_valid_until ON quotes (status, valid_until);

CREATE TABLE quote_add_ons (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    quote_id   BIGINT        NOT NULL,
    code       VARCHAR(40)   NOT NULL,
    name       VARCHAR(150)  NOT NULL,
    premium    DECIMAL(12,2) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    created_by VARCHAR(64),
    updated_at DATETIME(6)   NOT NULL,
    updated_by VARCHAR(64),
    version    BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_quote_add_ons_code UNIQUE (quote_id, code),
    CONSTRAINT fk_quote_add_ons_quote FOREIGN KEY (quote_id) REFERENCES quotes (id) ON DELETE CASCADE
);
