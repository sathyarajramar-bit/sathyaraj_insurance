# payment-service design (Phase 3D)

## 1. Responsibility
Collects the premium. One payment attempt per client `Idempotency-Key`, either for an APPROVED proposal
(purpose NEW_POLICY) or for a renewal-eligible policy (purpose RENEWAL). The amount is never taken from the
request: it is read from proposal-service / policy-service. A SUCCESS payment is the only thing that can make
policy-service issue a policy, and that fact leaves this service as the `PaymentSuccessful` event through a
transactional outbox. Refunds are an ADMIN action. It never issues policies and never decides renewal
eligibility (policy-service does, and exposes it on its GET).

## 2. Architecture
```text
client -> gateway -> payment-service ──Feign+SERVICE JWT──> proposal-service (APPROVED proposal: owner, premium)
                        │            ──Feign+SERVICE JWT──> policy-service   (renewal eligibility, premium; POST /issue via outbox)
                        ├── PaymentProvider port: MockPaymentProvider today (a Razorpay/Stripe adapter replaces it)
                        ├── retail_db (payments + payment_outbox), history table flyway_history_payment_service
                        ├── OutboxDispatcher: after-commit @Async fast path + OutboxRetryScheduler (cron 0 * * * * *)
                        │     └── OutboxTransport: HttpOutboxTransport (default) | KafkaOutboxTransport -> topic payment.succeeded
                        └── NotificationPublisher (common-lib): PAYMENT_SUCCESS / PAYMENT_FAILED / PAYMENT_REFUNDED
```
No cache. Resilience4j circuit breaker + retry (3 attempts, 300 ms exponential backoff, 503 fallback,
`BusinessException` ignored) on the proposal and policy reads; `PolicyGateway.issue` has the breaker only,
because the outbox owns retries with backoff. Feign 2 s connect / 5 s read. Port 8086.

## 3-4. Database (retail_db)
```text
payments        id, payment_reference (uk), idempotency_key (uk), status, purpose, user_id, customer_id, proposal_number,
                policy_number, amount DECIMAL(12,2), currency, payment_method, provider, provider_txn_id, failure_reason,
                refund_reference, refunded_at, refund_reason, completed_at, audit + version
                idx (user_id, created_at), idx (proposal_number, status), idx (policy_number, status)
payment_outbox  id, payment_reference, event_type, payload TEXT (JSON PaymentSuccessfulEvent), status, attempts, last_error,
                next_attempt_at, delivered_at, created_at;  uk (payment_reference, event_type), idx (status, next_attempt_at)
```
One row per attempt: FAILED attempts are kept and the same proposal is retried with a new key. `uk idempotency_key`
is the idempotency guarantee itself: a concurrent duplicate loses on insert and is answered with the winner's row.
Reference `PAY-yyyyMMdd-XXXXXXXX` (8 chars, 32-symbol alphabet without 0/O/1/I, SecureRandom). Enums: status
INITIATED | SUCCESS | FAILED | REFUNDED, purpose NEW_POLICY | RENEWAL, payment_method CARD | UPI | NET_BANKING,
outbox status PENDING | DELIVERED | FAILED.

## 5. API
```text
POST /api/payments {proposalNumber|policyNumber, paymentMethod, instrument}  CUSTOMER/AGENT/ADMIN; header Idempotency-Key (8-100 chars) required
                                                                            201 on SUCCESS, 422 on FAILED; same key -> original result, provider not called
GET  /api/payments/{reference}                                              owner, ADMIN, AGENT, SERVICE (policy-service re-verifies here)
GET  /api/payments?proposalNumber=&status=                                  own; ADMIN/AGENT all (paged, default sort createdAt desc)
POST /api/payments/{reference}/refund {reason}                              ADMIN; SUCCESS -> REFUNDED, reason mandatory
```

## 6. State machine
`INITIATED -> SUCCESS | FAILED`, `SUCCESS -> REFUNDED`. INITIATED lives only in memory between `prepare` and
`PaymentTransactions.record`, because the mock provider answers synchronously; a real asynchronous gateway adapter
(redirect + webhook) would persist INITIATED and complete it through the same service path. FAILED and REFUNDED
are terminal. The outbox row has its own `PENDING -> DELIVERED | FAILED`.

## 7. Business rules
- Exactly one of proposalNumber / policyNumber. The proposal must be APPROVED; the policy must be `renewalEligible`
  as computed by policy-service. The caller must own it (ADMIN/AGENT may pay for anyone).
- Amount = `proposal.premiumAmount` or `policy.premiumAmount`; currency from `payment.currency` (INR). A tampered
  request cannot underpay.
- Independently of the key: at most one SUCCESS per proposal and one renewal SUCCESS per policy
  (`existsBy...NumberAndStatus`).
- Idempotency-Key replay returns the stored payment whatever its status, without touching the provider.
- The provider call runs OUTSIDE any transaction (network call with money on the line). Then `record` writes
  the payment row, the outbox row (SUCCESS only) and publishes `OutboxCommittedEvent` in one short transaction.
  A provider exception becomes FAILED with "Payment gateway error: ...", never a 500.
- MockPaymentProvider: instrument ending `0002` -> declined ("Card declined by issuer"), `0009` -> gateway
  exception ("Mock gateway timeout"), anything else -> SUCCESS with a `mock_` txn id; refunds always succeed (`rfnd_`).
- Outbox: one delivery attempt right after commit; the scheduler (every minute) redelivers up to 50 PENDING rows
  whose `next_attempt_at` has passed; backoff `30 s * 2^(attempts-1)` capped at 1 h; FAILED after 10 attempts
  (`payment.outbox.cron|max-attempts|backoff-seconds`). The receiver is idempotent by paymentReference, so
  at-least-once delivery is safe.
- Refund only for SUCCESS: provider refund first, then REFUNDED with refund reference, reason and time.

## 8. Communication
Sync (Feign): one proposal or policy read before charging (owner, amount, eligibility). Outbox -> policy-service:
Feign `POST /api/policies/issue` by default; with `KAFKA_ENABLED=true` a record on `payment.succeeded` keyed by
paymentReference (see kafka-messaging-design.md). Either way the row becomes DELIVERED only after the call or the
broker ack succeeded. `OutboxCommittedEvent` carries the correlation id onto the @Async thread. Notifications
(after commit, async, fire-and-forget): PAYMENT_SUCCESS or PAYMENT_FAILED after `record` (amount, currency,
reference, reason), PAYMENT_REFUNDED after refund (amount, reason).

## 9. Security
Pay: CUSTOMER/AGENT/ADMIN, ownership checked against the proposal/policy `userId`. Read: owner, ADMIN, AGENT,
SERVICE. Search is filtered to the caller unless ADMIN/AGENT. Refund: ADMIN only. Downstream calls carry the
SERVICE JWT added by common-lib's Feign configuration. The amount never comes from the client.

## 10. Package structure
controller, service (PaymentService, PaymentTransactions, PaymentReferenceGenerator), provider (PaymentProvider
port, MockPaymentProvider), event (OutboxWriter, OutboxDispatcher, OutboxTransport, HttpOutboxTransport,
KafkaOutboxTransport), scheduler (OutboxRetryScheduler), client (ProposalClient/Gateway, PolicyClient/Gateway,
PaymentSuccessfulEvent), repository (+ PaymentSpecifications), entity (Payment, OutboxEvent, enums), dto, mapper,
exception, config (PaymentProperties).
