-- Reference data: two motor products so quote-service (Phase 3B) has something to price.
-- Seeded through Flyway so every environment (dev, test, docker) starts with the same catalogue.

INSERT INTO products (code, name, description, product_type, vehicle_type, coverage_type, term_months, active, effective_from, created_at, created_by, updated_at, updated_by, version)
VALUES ('MOTOR-CAR-COMP', 'Comprehensive Car Insurance', 'Own damage + third party liability for private cars, with optional add-ons.', 'MOTOR', 'CAR', 'COMPREHENSIVE', 12, TRUE, DATE '2026-01-01', CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0),
       ('MOTOR-BIKE-TP',  'Third Party Bike Insurance',  'Statutory third party liability cover for two-wheelers.',                  'MOTOR', 'BIKE', 'THIRD_PARTY',   12, TRUE, DATE '2026-01-01', CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0);

INSERT INTO product_coverages (product_id, code, name, description, sum_insured_type, fixed_sum_insured, mandatory, display_order, created_at, created_by, updated_at, updated_by, version)
SELECT id, 'OWN_DAMAGE', 'Own Damage', 'Accidental damage, fire, theft and natural calamities up to the IDV.', 'IDV', NULL, TRUE, 1, CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-CAR-COMP';
INSERT INTO product_coverages (product_id, code, name, description, sum_insured_type, fixed_sum_insured, mandatory, display_order, created_at, created_by, updated_at, updated_by, version)
SELECT id, 'THIRD_PARTY_LIABILITY', 'Third Party Liability', 'Legal liability for injury, death or property damage to third parties.', 'FIXED', 750000.00, TRUE, 2, CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-CAR-COMP';
INSERT INTO product_coverages (product_id, code, name, description, sum_insured_type, fixed_sum_insured, mandatory, display_order, created_at, created_by, updated_at, updated_by, version)
SELECT id, 'PA_OWNER_DRIVER', 'Personal Accident Owner-Driver', 'Compensation for death or permanent disability of the owner-driver.', 'FIXED', 1500000.00, TRUE, 3, CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-CAR-COMP';
INSERT INTO product_coverages (product_id, code, name, description, sum_insured_type, fixed_sum_insured, mandatory, display_order, created_at, created_by, updated_at, updated_by, version)
SELECT id, 'THIRD_PARTY_LIABILITY', 'Third Party Liability', 'Legal liability for injury, death or property damage to third parties.', 'FIXED', 100000.00, TRUE, 1, CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-BIKE-TP';

INSERT INTO product_add_ons (product_id, code, name, description, pricing_type, rate, active, created_at, created_by, updated_at, updated_by, version)
SELECT id, 'ZERO_DEPRECIATION', 'Zero Depreciation', 'Full claim amount without depreciation deduction on parts.', 'PERCENT_OF_IDV', 0.4000, TRUE, CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-CAR-COMP';
INSERT INTO product_add_ons (product_id, code, name, description, pricing_type, rate, active, created_at, created_by, updated_at, updated_by, version)
SELECT id, 'ENGINE_PROTECT', 'Engine Protection', 'Covers engine damage due to water ingression or oil leakage.', 'PERCENT_OF_IDV', 0.1500, TRUE, CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-CAR-COMP';
INSERT INTO product_add_ons (product_id, code, name, description, pricing_type, rate, active, created_at, created_by, updated_at, updated_by, version)
SELECT id, 'ROADSIDE_ASSISTANCE', 'Roadside Assistance', '24x7 towing, fuel delivery and on-spot repairs.', 'FLAT', 499.0000, TRUE, CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-CAR-COMP';
INSERT INTO product_add_ons (product_id, code, name, description, pricing_type, rate, active, created_at, created_by, updated_at, updated_by, version)
SELECT id, 'NCB_PROTECT', 'No Claim Bonus Protection', 'Keeps your NCB after one claim in the policy year.', 'PERCENT_OF_BASE_PREMIUM', 5.0000, TRUE, CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-CAR-COMP';
INSERT INTO product_add_ons (product_id, code, name, description, pricing_type, rate, active, created_at, created_by, updated_at, updated_by, version)
SELECT id, 'PA_PILLION', 'Pillion Rider Cover', 'Personal accident cover for the pillion rider.', 'FLAT', 150.0000, TRUE, CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-BIKE-TP';
