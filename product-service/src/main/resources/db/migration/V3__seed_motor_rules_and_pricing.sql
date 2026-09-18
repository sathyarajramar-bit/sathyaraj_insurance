-- Eligibility rules and pricing parameters for the seeded motor products.

INSERT INTO product_eligibility_rules (product_id, rule_type, rule_value, message, created_at, created_by, updated_at, updated_by, version)
SELECT id, 'MAX_VEHICLE_AGE_YEARS', '15', 'Comprehensive cover is available for cars up to 15 years old', CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-CAR-COMP';
INSERT INTO product_eligibility_rules (product_id, rule_type, rule_value, message, created_at, created_by, updated_at, updated_by, version)
SELECT id, 'MIN_IDV', '50000', 'Insured declared value must be at least 50,000', CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-CAR-COMP';
INSERT INTO product_eligibility_rules (product_id, rule_type, rule_value, message, created_at, created_by, updated_at, updated_by, version)
SELECT id, 'MIN_DRIVER_AGE', '18', 'Driver must be at least 18 years old', CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-CAR-COMP';
INSERT INTO product_eligibility_rules (product_id, rule_type, rule_value, message, created_at, created_by, updated_at, updated_by, version)
SELECT id, 'ALLOWED_FUEL_TYPES', 'PETROL,DIESEL,CNG,ELECTRIC,HYBRID', 'Fuel type is not covered by this product', CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-CAR-COMP';
INSERT INTO product_eligibility_rules (product_id, rule_type, rule_value, message, created_at, created_by, updated_at, updated_by, version)
SELECT id, 'MAX_VEHICLE_AGE_YEARS', '20', 'Third party cover is available for bikes up to 20 years old', CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-BIKE-TP';
INSERT INTO product_eligibility_rules (product_id, rule_type, rule_value, message, created_at, created_by, updated_at, updated_by, version)
SELECT id, 'MAX_ENGINE_CC', '1000', 'Bike engine capacity must not exceed 1000 cc', CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-BIKE-TP';

INSERT INTO product_pricing (product_id, base_rate_percent_of_idv, min_base_premium, third_party_premium, vehicle_age_loading_percent_per_year, max_vehicle_age_loading_percent, young_driver_age_limit, young_driver_loading_percent, max_ncb_discount_percent, electric_vehicle_discount_percent, tax_percent, created_at, created_by, updated_at, updated_by, version)
SELECT id, 2.5000, 2500.00, 3416.00, 2.0000, 20.0000, 25, 10.0000, 50.0000, 15.0000, 18.0000, CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-CAR-COMP';
INSERT INTO product_pricing (product_id, base_rate_percent_of_idv, min_base_premium, third_party_premium, vehicle_age_loading_percent_per_year, max_vehicle_age_loading_percent, young_driver_age_limit, young_driver_loading_percent, max_ncb_discount_percent, electric_vehicle_discount_percent, tax_percent, created_at, created_by, updated_at, updated_by, version)
SELECT id, 0.0000, 0.00, 714.00, 0.0000, 0.0000, 25, 0.0000, 0.0000, 0.0000, 18.0000, CURRENT_TIMESTAMP(6), 'system', CURRENT_TIMESTAMP(6), 'system', 0 FROM products WHERE code = 'MOTOR-BIKE-TP';
