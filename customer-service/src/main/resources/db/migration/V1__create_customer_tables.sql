-- customer_db: profile, KYC and vehicles. user_id references auth_db.users logically (no cross-db FK).

CREATE TABLE customers (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    email               VARCHAR(255) NOT NULL,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    phone               VARCHAR(20),
    date_of_birth       DATE,
    gender              VARCHAR(10),
    kyc_status          VARCHAR(20)  NOT NULL,
    kyc_document_type   VARCHAR(30),
    kyc_document_number VARCHAR(50),
    kyc_verified_at     DATETIME(6),
    address_line1       VARCHAR(255),
    address_line2       VARCHAR(255),
    city                VARCHAR(100),
    state               VARCHAR(100),
    postal_code         VARCHAR(20),
    country             VARCHAR(100),
    created_at          DATETIME(6)  NOT NULL,
    created_by          VARCHAR(64),
    updated_at          DATETIME(6)  NOT NULL,
    updated_by          VARCHAR(64),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_customers_user_id UNIQUE (user_id),
    CONSTRAINT uk_customers_email UNIQUE (email)
);

-- Supports the admin search (name filter) and KYC work queues.
CREATE INDEX idx_customers_last_name ON customers (last_name);
CREATE INDEX idx_customers_kyc_status ON customers (kyc_status);

CREATE TABLE vehicles (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id         BIGINT        NOT NULL,
    registration_number VARCHAR(20)   NOT NULL,
    vehicle_type        VARCHAR(10)   NOT NULL,
    make                VARCHAR(50)   NOT NULL,
    model               VARCHAR(50)   NOT NULL,
    variant             VARCHAR(50),
    fuel_type           VARCHAR(20)   NOT NULL,
    manufacturing_year  INT           NOT NULL,
    engine_capacity_cc  INT           NOT NULL,
    chassis_number      VARCHAR(30),
    registration_date   DATE          NOT NULL,
    current_value       DECIMAL(12,2) NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    created_by          VARCHAR(64),
    updated_at          DATETIME(6)   NOT NULL,
    updated_by          VARCHAR(64),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_vehicles_registration_number UNIQUE (registration_number),
    CONSTRAINT fk_vehicles_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);

CREATE INDEX idx_vehicles_customer_id ON vehicles (customer_id);
