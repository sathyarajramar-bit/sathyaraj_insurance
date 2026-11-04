# claims-service design (Phase 3F)

## 1. Responsibility
The claim lifecycle against an in-force policy: a customer registers an incident, a CLAIMS_HANDLER reviews,
asks for evidence, assesses, approves or rejects, settles and closes it, with a full audit trail. It does not
decide whether the policy was valid on the incident date (policy-service does, through the coverage check) and
does not store files (document-service does; claims only link to document ids after verifying them).

## 2. Architecture
```text
client -> gateway -> claims-service ──Feign+SERVICE JWT──> policy-service    GET /api/policies/{n}/coverage-check?incidentDate=
                        │            ──Feign+SERVICE JWT──> document-service  GET /api/documents/{id}  (exists, belongs to this claim / claimant)
                        ├── retail_db (claims, claim_documents, claim_status_history; flyway_history_claims_service)
                        ├── NotificationPublisher -> CLAIM_REGISTERED / DOCUMENTS_REQUIRED / APPROVED / REJECTED / SETTLED
                        └── PendingClaimReminderScheduler (cron claims.reminder-cron, default 09:30 daily)
```
Resilience4j circuit breaker + retry per downstream (`ClaimsGateways`), 503 naming the dependency as fallback;
`BusinessException`s are ignored by both so a 422 from policy-service is surfaced verbatim, not retried.

## 3-4. Database (retail_db)
```text
claims                id, claim_number (uk, CL-<year>-XXXXXXXX), status, user_id, customer_id, policy_number, product_code,
                      registration_number, idv, claim_type ACCIDENT|THEFT|FIRE|NATURAL_CALAMITY|THIRD_PARTY, incident_date,
                      incident_location, description, claimed_amount, assessed_amount, approved_amount, settled_amount,
                      settlement_reference, assessor, assessment_notes, decision_reason, documents_requested,
                      documents_requested_at, settled_at, closed_at, reopened_count, audit + version
                      idx (user_id, created_at)  idx (policy_number)  idx (status, documents_requested_at) <- reminder query
claim_documents       id, claim_id (fk, cascade), document_id (document-service id), document_type, file_name, attached_by,
                      attached_at            uk (claim_id, document_id)
claim_status_history  id, claim_id (fk, cascade), from_status, to_status, changed_by, reason, changed_at   (append-only)
                      idx (claim_id, changed_at)
```
Policy facts (product, registration, IDV, owner) are snapshotted at registration so the claim stays stable
if the policy later expires or is cancelled.

## 5. API
```text
POST /api/claims {policyNumber, claimType, incidentDate, incidentLocation, description, claimedAmount}
                                                   CUSTOMER/AGENT/ADMIN      201 REGISTERED
GET  /api/claims/{number}                          owner, ADMIN, CLAIMS_HANDLER, AGENT, SERVICE
GET  /api/claims/{number}/history                  same
GET  /api/claims?customerId=&policyNumber=&status=&type=&incidentFrom=&incidentTo=&page=&size=&sort=
                                                   own claims; ADMIN/CLAIMS_HANDLER/AGENT any (customerId filter is staff only)
PUT  /api/claims/{number}/documents {documentIds}  owner, CLAIMS_HANDLER, ADMIN   verified against document-service; DOCUMENTS_REQUIRED -> UNDER_REVIEW
PUT  /api/claims/{number}/review                   CLAIMS_HANDLER/ADMIN      -> UNDER_REVIEW, assessor = caller
PUT  /api/claims/{number}/assessment {assessedAmount, notes}   CLAIMS_HANDLER/ADMIN   only while UNDER_REVIEW
PUT  /api/claims/{number}/documents-required {documentsRequested}   CLAIMS_HANDLER/ADMIN   -> DOCUMENTS_REQUIRED + notification
PUT  /api/claims/{number}/approve {approvedAmount, reason}          CLAIMS_HANDLER/ADMIN   -> APPROVED
PUT  /api/claims/{number}/reject {reason}          CLAIMS_HANDLER/ADMIN      -> REJECTED
PUT  /api/claims/{number}/settle {settlementReference}   CLAIMS_HANDLER/ADMIN   -> SETTLED, settled_amount = approved_amount
PUT  /api/claims/{number}/close {reason}           CLAIMS_HANDLER/ADMIN      -> CLOSED
PUT  /api/claims/{number}/reopen {reason}          ADMIN                     CLOSED -> UNDER_REVIEW, reopened_count++
```

## 6. State machine
```text
REGISTERED -> UNDER_REVIEW | DOCUMENTS_REQUIRED | REJECTED
UNDER_REVIEW -> DOCUMENTS_REQUIRED | APPROVED | REJECTED
DOCUMENTS_REQUIRED -> UNDER_REVIEW (documents attached) | REJECTED
APPROVED -> SETTLED -> CLOSED          REJECTED -> CLOSED
CLOSED -> UNDER_REVIEW only through the explicit ADMIN reopen
```
`ClaimStatus.canTransitionTo` validates every move; `Claim.transition` applies it and appends the history row.
SETTLED is reachable only from APPROVED, so "settlement only after approval" is a property of the machine.

## 7. Business rules
- Registration: incident not older than `claims.max-days-after-incident` (30); policy-service confirms the policy
  covers the incident date (422 with its message otherwise); the policy must belong to the caller unless the caller
  is AGENT, CLAIMS_HANDLER or ADMIN; `claimedAmount <= idv`; a THIRD_PARTY claim needs third-party cover, and a
  THIRD_PARTY-only policy accepts only THIRD_PARTY claims.
- Documents: each id must exist in document-service, carry `referenceType CLAIM` + this claim number, and (for a
  customer) be owned by the caller; duplicates are skipped; nothing can be added to a SETTLED/REJECTED/CLOSED claim.
- Assessment only while UNDER_REVIEW and `assessedAmount <= idv`. Approval needs `approvedAmount <= claimedAmount`
  and `<= idv`. Rejection and close need a reason. Reopen is ADMIN only, reason mandatory, CLOSED only.
- Reminder job: every claim in DOCUMENTS_REQUIRED for more than `claims.pending-documents-reminder-days` (7) gets a
  CLAIM_DOCUMENTS_REQUIRED notification with key `CLAIM_DOCUMENTS_REMINDER:<claim>:<day>`, so at most one per day.

## 8. Communication
Sync: coverage check at registration (cannot register without it), document metadata at attach time.
Async: notifications after commit for every customer-visible decision; nothing is sent for a rolled-back change.
Inbound from other services: none today (SERVICE may read).

## 9. Security
`ClaimAccessPolicy`: claimant reads and attaches on own claims; CLAIMS_HANDLER and ADMIN handle every claim; AGENT and
SERVICE read. Customers listing with `customerId=` get 403. Every handler endpoint is `@PreAuthorize`d; the
state machine is the second guard.

## 10. Package structure
controller (ClaimController), service (ClaimService customer side, ClaimHandlingService handler side, ClaimAccessPolicy,
ClaimNumberGenerator), client (PolicyClient, DocumentClient, ClaimsGateways), scheduler (PendingClaimReminderScheduler),
config (ClaimsProperties), entity (Claim, ClaimDocument, ClaimStatus, ClaimStatusHistory, ClaimType), repository
(+ ClaimSpecifications), dto, mapper, exception (ClaimException 422, ClaimNotFoundException 404).
