# proposal-service design (Phase 3C)

## 1. Responsibility
The formal application for insurance. Created from an ACCEPTED quote, completed by the customer
(proposer, nominee, declarations), submitted, then reviewed by an AGENT/ADMIN. An APPROVED proposal is
the input of payment-service (amount to collect) and policy-service (what to issue). It never prices and
never issues: those stay in quote-service and policy-service.

## 2. Architecture
```text
client -> gateway -> proposal-service ──Feign+SERVICE JWT──> quote-service    (accepted quote, premium)
                        │              ──Feign+SERVICE JWT──> customer-service (prefill, KYC status at submission)
                        ├── proposal_db (proposals + proposal_status_history)
                        └── ProposalSubmitted / ProposalDecided events (after-commit, in-process today)
```
No cache: a proposal is read a handful of times by one customer and one reviewer. Resilience4j circuit
breaker + retry on both downstreams, 503 fallback. No scheduler yet (a "pending review reminder" job is a
natural later addition).

## 3-4. Database (proposal_db)
```text
proposals                id, proposal_number (uk), status, user_id, customer_id, quote_number (uk), quote_id,
                         product_id/code/name, coverage_type, term_months, vehicle_id, registration_number, idv,
                         premium_amount, proposer_* (name, email, phone, dob, address), nominee_* (name, relationship, dob),
                         declarations_accepted, submitted_at, reviewed_by, reviewed_at, decision_reason, audit + version
                         idx (user_id, created_at), idx (customer_id), idx (status, submitted_at)  <- review queue
proposal_status_history  id, proposal_id (fk), from_status, to_status, changed_by, reason, changed_at   (append-only)
```
One proposal per quote (unique `quote_number`). Quote facts are snapshotted so the proposal is stable
even if the quote later expires. Proposer/nominee are `@Embeddable` value objects.

## 5. API
```text
POST /api/proposals {quoteNumber}          CUSTOMER/AGENT/ADMIN   DRAFT prefilled from customer profile (201)
GET  /api/proposals/{number}               owner, ADMIN, AGENT, SERVICE
GET  /api/proposals/{number}/history       owner, ADMIN, AGENT, SERVICE
GET  /api/proposals?status=&customerId=    own; AGENT/ADMIN any customer (paged, sorted)
PUT  /api/proposals/{number}               owner; DRAFT only: proposer, nominee, declarations
POST /api/proposals/{number}/submit        owner; DRAFT -> SUBMITTED; idempotent on SUBMITTED
POST /api/proposals/{number}/review        AGENT/ADMIN; SUBMITTED -> UNDER_REVIEW
POST /api/proposals/{number}/approve       AGENT/ADMIN; SUBMITTED|UNDER_REVIEW -> APPROVED
POST /api/proposals/{number}/reject        AGENT/ADMIN; reason mandatory -> REJECTED
```

## 6. State machine
`DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED | REJECTED` (SUBMITTED may be decided directly).
Transitions are validated in `ProposalStatus.canTransitionTo` and applied only through
`Proposal.transition`, which appends the history row (who, from, to, why, when). Final states are terminal.

## 7. Business rules
- Quote must be ACCEPTED, not expired (`QUOTE_EXPIRED`), and belong to the caller (AGENT/ADMIN may act for a customer).
- Submission requires complete proposer (incl. address and date of birth), proposer age >= 18, nominee,
  declarations accepted, and KYC VERIFIED (configurable `proposal.require-kyc-verified`).
- Edits only while DRAFT; rejection needs a reason.
- Submission is idempotent: a retried submit of a SUBMITTED proposal returns 200 with the same state
  instead of failing, so client retries after a timeout are safe.

## 8. Communication
Sync (Feign): quote and customer reads at creation/submission, because the proposal cannot be built or
validated without them. Async (events, after commit): ProposalSubmitted (customer confirmation, agent
queue) and ProposalDecided (APPROVED -> payment may proceed, REJECTED -> notify with reason).
`@TransactionalEventListener` guarantees consumers never see a rolled-back state.

## 9. Security
Owner edits/submits; AGENT/ADMIN review and see everything; SERVICE (payment/policy) reads. Every
controller method is role-gated; ownership is enforced in `ProposalAccessPolicy`.

## 10. Package structure
controller, service (ProposalService, ProposalNumberGenerator, ProposalAccessPolicy), client (contracts +
gateways), repository, entity (Proposal, Proposer, Nominee, ProposalStatusHistory, enums), dto, mapper,
exception, config, event.
