# product-service design (Phase 3A)

## 1. Responsibility
The catalogue of what the platform sells. It answers: which products exist, what they cover, which add-ons
they offer, who is eligible, and at which rates they are priced. It does **not** price a specific customer
(quote-service), hold customer data (customer-service) or issue policies (policy-service).

## 2. Architecture
Spring Boot service on port 8083, registered in Eureka, configured by the Config Server, protected by the
shared JWT filter from common-lib, backed by `product_db` (MySQL/Flyway) and Redis (Spring Cache).
A product is an aggregate: product row + coverages + add-ons + eligibility rules + pricing are written
together and cached together.

## 3-4. Database (product_db)
```text
products                    id, code (uk), name, description, product_type, vehicle_type, coverage_type,
                            term_months, active, effective_from, effective_to, audit + version
                            idx (product_type, active), idx (vehicle_type)
product_coverages           id, product_id (fk), code, name, description, sum_insured_type, fixed_sum_insured,
                            mandatory, display_order            uk (product_id, code)
product_add_ons             id, product_id (fk), code, name, description, pricing_type, rate, active
                                                                 uk (product_id, code)
product_eligibility_rules   id, product_id (fk), rule_type, rule_value, message   uk (product_id, rule_type)
product_pricing             id, product_id (fk, uk), base_rate_percent_of_idv, min_base_premium,
                            third_party_premium, vehicle_age_loading_percent_per_year,
                            max_vehicle_age_loading_percent, young_driver_age_limit,
                            young_driver_loading_percent, max_ncb_discount_percent,
                            electric_vehicle_discount_percent, tax_percent
```
Children cascade from the product (`ON DELETE CASCADE`, JPA `orphanRemoval`). Products are never
hard-deleted (quotes/policies reference them): DELETE = deactivate. V2/V3 seed two motor products.

## 5. API
```text
GET    /api/products?type=&vehicleType=&coverageType=&active=&page=&size=&sort=   public (non-admin: active only)
GET    /api/products/{id}                          public: coverages, add-ons, rules (no pricing)
GET    /api/products/{id}/pricing                  ADMIN, AGENT, SERVICE
POST   /api/products/{id}/eligibility-check        any authenticated caller (quote-service uses SERVICE)
POST   /api/products                               ADMIN
PUT    /api/products/{id}                          ADMIN (aggregate replace, code immutable)
DELETE /api/products/{id}                          ADMIN (deactivate)
PATCH  /api/products/{id}/activate                 ADMIN
```

## 6. Dependencies
Outbound: none (leaf service). Inbound: quote-service (details, pricing, eligibility), policy-service
(product snapshot at issuance), admin UI. Synchronous REST is right here: the catalogue is read-mostly,
small and cached; there is no business event worth publishing except "product changed", which the
cache eviction handles inside the service.

## 7. Redis
| Cache | Key | TTL | Evicted by |
|---|---|---|---|
| `products` | product id | 1 h | update, activate/deactivate of that id |
| `product-catalog` | filters + page + size + sort | 10 min | any product write (all entries) |
| `product-pricing` | product id | 1 h | update of that id |

Values are typed JSON (no class metadata). A Redis outage is logged by the `CacheErrorHandler` and the
request falls through to MySQL. Eligibility checks are not cached (input specific, microseconds).

## 8. Security
Browsing is public at the gateway and in the service (`GET /api/products/**`). Writes need ADMIN
(method security). Pricing is hidden from customers: it is read by quote-service with a SERVICE token.
Anonymous callers hitting a protected method get 401, authenticated ones without the role get 403.

## 9. Package structure
```text
com.insurance.product
  controller   ProductController
  service      ProductService (cache annotations, aggregate merge), ProductRequestValidator, EligibilityService
  eligibility  RiskProfile, EligibilityResult, EligibilityEvaluator (pure domain logic)
  repository   ProductRepository, ProductSpecifications
  entity       Product, Coverage, AddOn, EligibilityRule, PricingConfig + enums
  dto          requests, responses, RiskProfileRequest, EligibilityResponse
  mapper       ProductMapper (MapStruct, builders disabled)
  exception    ProductNotFoundException
  config       CacheNames, ProductCacheProperties, RedisCacheConfig
```
`client`, `event`, `scheduler` are intentionally absent: this service has no outbound calls, no events and no jobs.

## 10. End-to-end position
Customer -> **browse products (public, cached)** -> select product -> quote-service reads product,
pricing and eligibility -> premium -> proposal -> payment -> policy (stores product code + snapshot).
