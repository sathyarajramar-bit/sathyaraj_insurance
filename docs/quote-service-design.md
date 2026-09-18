# quote-service design (Phase 3B)

## 1. Responsibility
Turn "this customer, this vehicle, this product, these add-ons" into a priced, time-limited quote and
manage its lifecycle (GENERATED -> ACCEPTED | EXPIRED | CANCELLED). It owns the premium formula; the
rates come from product-service, the vehicle and customer facts from customer-service.

## 2. Architecture
```text
client -> gateway -> quote-service ──Feign+SERVICE JWT──> customer-service  (profile by user, vehicle)
                        │           ──Feign+SERVICE JWT──> product-service   (product, pricing, eligibility)
                        ├── PremiumCalculator (pure)      ── QuoteExpiryScheduler (cron)
                        ├── quote_db (MySQL/Flyway)       ── Redis cache: quotes::<number>
                        └── QuoteGeneratedEvent (in-process today, Kafka-ready)
```
Remote reads happen before the transaction; only the insert is transactional. Each Feign gateway has a
Resilience4j circuit breaker + retry; the Feign read timeout bounds each attempt.

## 3-4. Database (quote_db)
```text
quotes         id, quote_number (uk), status, user_id, customer_id, product_id/code/name, coverage_type,
               term_months, vehicle_id + vehicle snapshot (registration, type, make, model, fuel, year, cc),
               idv, driver_age, ncb_percent, own_damage/third_party/base/add_on/discount/tax/final premium,
               valid_until, accepted_at, cancelled_at, audit + version
               idx (user_id, created_at), idx (customer_id), idx (status, valid_until)
quote_add_ons  id, quote_id (fk), code, name, premium          uk (quote_id, code)
```
A quote is a snapshot: product and vehicle can change afterwards without altering what was offered.

## 5. API
```text
POST /api/quotes/preview                 CUSTOMER/AGENT/ADMIN  price only (status DRAFT, not stored)
POST /api/quotes                         CUSTOMER/AGENT/ADMIN  generate (201, GENERATED, valid 15 days)
GET  /api/quotes/{quoteNumber}           owner, ADMIN, AGENT, SERVICE (cached)
GET  /api/quotes?status=&productId=&customerId=&page=&size=&sort=   own quotes; ADMIN/AGENT any customer
POST /api/quotes/{quoteNumber}/accept    owner/AGENT/ADMIN     GENERATED -> ACCEPTED (expired -> 422 QUOTE_EXPIRED)
POST /api/quotes/{quoteNumber}/cancel    owner/AGENT/ADMIN
```
Request: `productId, vehicleId, addOnCodes[], driverAge?, ncbPercent?, customerId? (AGENT only)`.

## 6. Dependencies and communication style
Synchronous (Feign): customer profile + vehicle, product details + pricing + eligibility. These are
reads the quote cannot be produced without, so the customer must wait for them; async would only add
latency and complexity. Asynchronous (event): QuoteGenerated -> notification. The customer must get the
quote even if e-mail is down, so notifying is a side effect off the request path.

## 7. Premium formula
```text
ownDamage  = max(IDV x baseRate%, minBasePremium) x (1 + min(age x ageLoading%, maxAgeLoading%)) x (1 + youngDriver%)
thirdParty = flat statutory premium
base       = ownDamage + thirdParty
addOns     = PERCENT_OF_IDV | PERCENT_OF_BASE_PREMIUM | FLAT
discount   = ownDamage x min(ncb%, maxNcb%) + ownDamage x evDiscount%
tax        = (base + addOns - discount) x tax%
final      = base + addOns - discount + tax
```
Rounded HALF_UP to 2 decimals per line so the breakdown adds up exactly; verified to the paisa in tests.

## 8. Redis
`quotes::<number>` (TTL 15 min), typed JSON. Evicted on accept/cancel; the expiry job clears the cache
after a bulk update. The cached load contains no authorization (a cached method must not contain
per-caller logic); the ownership check runs on every call on the DTO.

## 9. Security
Customers see and change only their own quotes. AGENT/ADMIN may quote on behalf of a customer
(`customerId`) and see all quotes. SERVICE (proposal/payment) may read. Anonymous -> 401.

## 10. Resilience
Per downstream: circuit breaker (10-call window, opens at 50% failures, 20 s open, 3 half-open trials),
retry (3 attempts, exponential backoff) for idempotent reads only. Business errors (404/422) are ignored
by both. Fallback raises 503 `SERVICE_UNAVAILABLE` naming the dependency; nothing is invented.

## 11. Scheduler
`quote.expiry.cron` (default every 10 min) runs one set-based UPDATE GENERATED -> EXPIRED for overdue
quotes; `-` disables it. Reads already treat an overdue quote as expired, so the job is hygiene, not
correctness. Multi-instance: idempotent; ShedLock would make it single-run.

## 12. Package structure
controller, service (QuoteService, QuotePersistence, QuoteReadCache, QuoteNumberGenerator, QuoteAccessPolicy),
pricing (engine), client (Feign contracts + resilient gateways), repository, entity, dto, mapper,
exception, config, scheduler, event.
