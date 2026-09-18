-- product_db: the catalogue. Referenced by quote/policy services by product id and code only (no cross-db FKs).

CREATE TABLE products (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(40)  NOT NULL,
    name           VARCHAR(150) NOT NULL,
    description    VARCHAR(1000),
    product_type   VARCHAR(20)  NOT NULL,   -- MOTOR | HEALTH | TRAVEL | LIFE
    vehicle_type   VARCHAR(10),             -- CAR | BIKE (motor only)
    coverage_type  VARCHAR(20)  NOT NULL,   -- THIRD_PARTY | COMPREHENSIVE | OWN_DAMAGE
    term_months    INT          NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    effective_from DATE         NOT NULL,
    effective_to   DATE,
    created_at     DATETIME(6)  NOT NULL,
    created_by     VARCHAR(64),
    updated_at     DATETIME(6)  NOT NULL,
    updated_by     VARCHAR(64),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_products_code UNIQUE (code)
);

-- Catalogue browsing filters: type + active, and vehicle type.
CREATE INDEX idx_products_type_active ON products (product_type, active);
CREATE INDEX idx_products_vehicle_type ON products (vehicle_type);

CREATE TABLE product_coverages (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id        BIGINT        NOT NULL,
    code              VARCHAR(40)   NOT NULL,
    name              VARCHAR(150)  NOT NULL,
    description       VARCHAR(500),
    sum_insured_type  VARCHAR(20)   NOT NULL,   -- IDV | FIXED
    fixed_sum_insured DECIMAL(14,2),
    mandatory         BOOLEAN       NOT NULL DEFAULT TRUE,
    display_order     INT           NOT NULL DEFAULT 0,
    created_at        DATETIME(6)   NOT NULL,
    created_by        VARCHAR(64),
    updated_at        DATETIME(6)   NOT NULL,
    updated_by        VARCHAR(64),
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_coverages_code UNIQUE (product_id, code),
    CONSTRAINT fk_product_coverages_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

CREATE TABLE product_add_ons (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id   BIGINT        NOT NULL,
    code         VARCHAR(40)   NOT NULL,
    name         VARCHAR(150)  NOT NULL,
    description  VARCHAR(500),
    pricing_type VARCHAR(30)   NOT NULL,   -- PERCENT_OF_IDV | PERCENT_OF_BASE_PREMIUM | FLAT
    rate         DECIMAL(12,4) NOT NULL,   -- percent (e.g. 0.5000) or flat amount depending on pricing_type
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   DATETIME(6)   NOT NULL,
    created_by   VARCHAR(64),
    updated_at   DATETIME(6)   NOT NULL,
    updated_by   VARCHAR(64),
    version      BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_add_ons_code UNIQUE (product_id, code),
    CONSTRAINT fk_product_add_ons_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

CREATE TABLE product_eligibility_rules (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT       NOT NULL,
    rule_type  VARCHAR(40)  NOT NULL,   -- MAX_VEHICLE_AGE_YEARS, MIN_DRIVER_AGE, ALLOWED_FUEL_TYPES, ...
    rule_value VARCHAR(100) NOT NULL,   -- "15" or "PETROL,DIESEL"
    message    VARCHAR(255) NOT NULL,   -- shown to the customer when the rule fails
    created_at DATETIME(6)  NOT NULL,
    created_by VARCHAR(64),
    updated_at DATETIME(6)  NOT NULL,
    updated_by VARCHAR(64),
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_rules_type UNIQUE (product_id, rule_type),
    CONSTRAINT fk_product_rules_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

-- One pricing configuration per product; the quote engine reads these parameters.
CREATE TABLE product_pricing (
    id                                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id                           BIGINT        NOT NULL,
    base_rate_percent_of_idv             DECIMAL(8,4)  NOT NULL,   -- own-damage premium = IDV * rate%
    min_base_premium                     DECIMAL(12,2) NOT NULL,
    third_party_premium                  DECIMAL(12,2) NOT NULL,   -- flat statutory component
    vehicle_age_loading_percent_per_year DECIMAL(8,4)  NOT NULL,
    max_vehicle_age_loading_percent      DECIMAL(8,4)  NOT NULL,
    young_driver_age_limit               INT           NOT NULL,
    young_driver_loading_percent         DECIMAL(8,4)  NOT NULL,
    max_ncb_discount_percent             DECIMAL(8,4)  NOT NULL,
    electric_vehicle_discount_percent    DECIMAL(8,4)  NOT NULL,
    tax_percent                          DECIMAL(8,4)  NOT NULL,
    created_at                           DATETIME(6)   NOT NULL,
    created_by                           VARCHAR(64),
    updated_at                           DATETIME(6)   NOT NULL,
    updated_by                           VARCHAR(64),
    version                              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_pricing_product UNIQUE (product_id),
    CONSTRAINT fk_product_pricing_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);
