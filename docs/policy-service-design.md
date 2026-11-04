# policy-service design (Phase 3E)

## 1. Responsibility
The issued contract. A policy is created only from a SUCCESS payment (`PaymentSuccessful` event), snapshots the
APPROVED proposal (product, vehicle, IDV, holder, nominee, term) so it stays stable whatever happens upstream, and
then lives its own life: ACTIVE, expired by a nightly job, cancelled by the customer, renewed by paying again in
payment-service. It answers claims-service's "was this policy in force on the incident date?" and stores the
policy schedule in document-service. It never collects money and never prices.

## 2. Architecture
```text
payment-service ──outbox: Feign POST /issue (default) | Kafka payment.succeeded──> policy-service
client -> gateway ───────────────────────────────────────────────────────────────> │ ──Feign+SERVICE JWT──> payment-service  (re-verify SUCCESS)
                                                                                  │ ──Feign+SERVICE JWT──> proposal-service (APPROVED snapshot)
                                                                                  │ ──Feign+SERVICE JWT──> document-service (POST /api/documents/generated, @Async)
                                                                                  ├── retail_db (policies + policy_renewal_reminders), flyway_history_policy_service
                                                                                  ├── PolicyLifecycleScheduler: expiry 0 5 0 * * *, reminders 0 15 8 * * *
                                                                                  └── NotificationPublisher: POLICY_ISSUED / POLICY_CANCELLED / POLICY_EXPIRED / POLICY_RENEWAL_REMINDER
```
No cache. Resilience4j circuit breaker + retry (3 attempts, 300 ms exponential backoff, 503 fallback) on the
proposal and payment reads in `DownstreamGateways`; the document call has none because it runs asynchronously and
a failure only leaves `schedule_document_id` null. Feign 2 s connect / 5 s read. Port 8087.

## 3-4. Database (retail_db)
```text
policies                  id, policy_number (uk), status, user_id, customer_id, proposal_number (uk), quote_number, payment_reference (uk),
                          product_id/code/name, coverage_type, term_months, vehicle_id, registration_number, idv DECIMAL(14,2),
                          premium_amount DECIMAL(12,2), holder_name, holder_email, nominee_name, nominee_relationship, start_date, end_date,
                          issued_at, renewed_from_policy_number, renewed_by_policy_number, cancelled_at, cancellation_reason,
                          schedule_document_id, audit + version
                          idx (user_id, created_at), idx (customer_id), idx (status, end_date) <- expiry + reminder jobs, idx (registration_number)
policy_renewal_reminders  id, policy_number, days_before, sent_at;  uk (policy_number, days_before)   (append-only)
```
The three unique keys make issuance idempotent: a redelivered event finds the row by `payment_reference`, and a
concurrent duplicate insert fails on the constraint and is answered with the existing policy. A renewal is a new
row (new policy number, new payment reference) linked both ways through `renewed_from/by_policy_number`. Policy
number `PL-<line>-<year>-XXXXXXXX`: line = product code before the first `-`, year UTC, 8 random chars from a
32-symbol alphabet. Status PENDING | ACTIVE | EXPIRED | CANCELLED.

## 5. API
```text
POST /api/policies/issue  {PaymentSuccessfulEvent}                 SERVICE only; idempotent by paymentReference; payment re-verified
GET  /api/policies/{policyNumber}                                   owner, ADMIN, AGENT, CLAIMS_HANDLER, SERVICE; includes renewalEligible + message
GET  /api/policies/{policyNumber}/coverage-check?incidentDate=      SERVICE/ADMIN/AGENT/CLAIMS_HANDLER; policy if in force that day, else 422
GET  /api/policies?status=&customerId=&registrationNumber=          own; ADMIN/AGENT/CLAIMS_HANDLER/SERVICE any (paged, sort createdAt desc)
POST /api/policies/{policyNumber}/cancel {reason}                   owner, AGENT, ADMIN; ACTIVE -> CANCELLED, reason mandatory
POST /api/policies/{policyNumber}/renew                             CUSTOMER/AGENT/ADMIN; returns eligibility only, renewal is paid in payment-service
```

## 6. State machine
`PENDING -> ACTIVE -> EXPIRED | CANCELLED`. Issuance writes ACTIVE directly (PENDING is declared, RenewalPolicy
treats it as not renewable, but no code path assigns it today). ACTIVE -> EXPIRED is done by the expiry job in one
bulk UPDATE (`updated_by = 'policy-expiry-job'`); ACTIVE -> CANCELLED by `PolicyService.cancel`. EXPIRED and
CANCELLED are terminal; renewal does not reopen a policy, it creates the next one.

## 7. Business rules
- Issue: the event is never trusted alone. The payment is re-read from payment-service and must be SUCCESS; for
  NEW_POLICY the proposal must be APPROVED; `premium_amount` is the paid amount. A forged `payment.succeeded`
  record cannot issue a policy. Business violations are `PolicyException` (422).
- New policy: `start_date = today`, `end_date = start + term_months - 1 day`, holder from proposer, nominee
  optional.
- Renewal (purpose RENEWAL): the old policy must pass `RenewalPolicy`; cover continues from `old.end_date + 1`
  when paid in time, else from today; everything else is copied from the old row, `proposal_number` becomes
  `old + "/R1"` (`/R2` if the old policy was itself a renewal), and the old row gets `renewed_by_policy_number`.
- `RenewalPolicy` (one place): not eligible if already renewed, CANCELLED or PENDING; eligible from
  `end_date - renewalWindowDays` (30) to `end_date + renewalGraceDays` (30), i.e. ACTIVE near expiry or EXPIRED
  within grace; beyond that a new quote is required. payment-service reads this flag before accepting a renewal.
- Cancel: owner/AGENT/ADMIN, ACTIVE only. Any refund is a separate ADMIN action in payment-service.
- Coverage check: ACTIVE and `start_date <= incidentDate <= end_date`, otherwise 422 with the period in the message.
- Schedule document: `PolicyScheduleGenerator` renders a plain-text certificate, `PolicyDocumentService` stores it
  @Async as `POLICY / POLICY_SCHEDULE / <policy>-schedule.txt` (base64 JSON, no multipart) and links the returned
  id; issuance never waits for or fails because of it.
- Expiry job (`policy.jobs.expiry-cron`, 00:05 daily): ACTIVE with `end_date < today` -> EXPIRED, one
  POLICY_EXPIRED each (with grace end). Reminder job (`policy.jobs.reminder-cron`, 08:15 daily): for each of
  `reminder-days-before` 30,15,7, ACTIVE policies ending exactly that day, skipped if already renewed or already
  reminded (`policy_renewal_reminders`), idempotency key `POLICY_RENEWAL_REMINDER:<policy>:<days>`. Both jobs are
  idempotent, so several instances may run them.

## 8. Communication
Inbound: `POST /api/policies/issue` from payment-service's outbox (default) or `PaymentSucceededListener` on
topic `payment.succeeded`, group `policy-service`, deserialised into `IssuePolicyRequest` (mirror of
`PaymentSuccessfulEvent`); both call the same `PolicyIssuanceService.issue` (see kafka-messaging-design.md for
retries and the DLT). Sync (Feign): payment and proposal reads during issue, document store after it. Outbound to
payment-service: nothing; payment-service reads `GET /api/policies/{n}` for renewal pricing and eligibility.
Notifications (after commit, async): POLICY_ISSUED (to holder e-mail: product, dates, premium), POLICY_CANCELLED
(reason), POLICY_EXPIRED (end date, grace end), POLICY_RENEWAL_REMINDER (end date, days left, premium).

## 9. Security
`/issue` is SERVICE only and still re-verifies the payment. Reads: owner, or ADMIN/AGENT/CLAIMS_HANDLER/SERVICE
(`PolicyService.assertCanRead`); a customer passing `customerId=` on the list is refused. Cancel: owner, AGENT,
ADMIN. Coverage check: SERVICE/ADMIN/AGENT/CLAIMS_HANDLER. Downstream calls carry the SERVICE JWT.

## 10. Package structure
controller, service (PolicyService, PolicyIssuanceService, PolicyTransactions, RenewalPolicy,
PolicyNumberGenerator, PolicyScheduleGenerator, PolicyDocumentService), messaging (PaymentSucceededListener),
scheduler (PolicyLifecycleScheduler), client (PaymentClient, ProposalClient, DocumentClient, DownstreamGateways),
repository (+ PolicySpecifications, RenewalReminderRepository), entity (Policy, RenewalReminder, PolicyStatus),
dto, mapper, exception, config (PolicyProperties).
