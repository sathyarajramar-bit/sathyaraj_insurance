# notification-service design (Phase 3H)

## 1. Responsibility
Turns business events from every other service into messages to customers: resolves the recipient, renders a
template per event type, stores one row per channel (EMAIL, SMS), delivers through a provider and retries
failures. Producers never talk to an e-mail server and never wait for delivery; they get a 202 (or a Kafka
ack) and move on. Nothing here decides *whether* to notify: the producer does, by publishing.

## 2. Architecture
```text
auth / quote / proposal / payment / policy / claims
   └─ NotificationPublisher (common-lib, after commit, async)
        ├─ default: Feign POST /api/notifications (SERVICE JWT)          ─┐
        └─ KAFKA_ENABLED=true: topic notification.requested              ─┤ NotificationRequestedListener (group notification-service, 3 threads)
                                                                          ▼
                                                   NotificationService.accept()
                                                     RecipientResolver ──Feign──> customer-service (e-mail, phone, first name) when the request has none
                                                     NotificationTemplates.render(eventType, params) -> subject, email body, sms body
                                                     store PENDING per channel (idempotent) -> deliver via ChannelProvider -> SENT | FAILED
                        ├── retail_db (notifications; flyway_history_notification_service)
                        └── NotificationRetryScheduler (cron notification.retry.cron, default every 5 min)
```
Providers: `MockEmailProvider` (logs), `SmtpEmailProvider` (`NOTIFICATION_EMAIL_PROVIDER=SMTP`, `spring.mail.*`, Gmail
587 STARTTLS + App Password by default), `MockSmsProvider`. Circuit breaker + retry on customer-service; when it is
down the recipient is null, the row is stored FAILED and picked up by the retry job.

## 3-4. Database (retail_db)
```text
notifications   id, idempotency_key, event_type, channel EMAIL|SMS, user_id, customer_id, recipient (address or phone),
                reference_type, reference_number (e.g. POLICY / PL-...), subject, body TEXT, status PENDING|SENT|FAILED,
                attempts, last_error, provider, provider_message_id, sent_at, created_at, updated_at
                uk (idempotency_key, channel)   <- de-duplication
                idx (user_id, created_at)  <- my notifications    idx (status, updated_at)  <- retry query
                idx (reference_type, reference_number)  <- staff lookup per policy / claim
```
Not an `AuditableEntity`: rows are written by the service itself, there is no human actor to audit.

## 5. API
```text
POST /api/notifications  NotificationRequest                       SERVICE only   202 + the rows created (0 on a replay)
GET  /api/notifications?page=&size=&sort=                           bearer         my notifications (by JWT user id)
GET  /api/notifications?referenceType=&referenceNumber=             ADMIN, AGENT, CLAIMS_HANDLER   everything sent for one policy / claim / proposal
```
`NotificationRequest` (common-lib): `eventType, userId, customerId, recipientEmail, recipientPhone, referenceType,
referenceNumber, params, idempotencyKey`. `NotificationRequest.of(...)` sets the key to `<eventType>:<referenceNumber>`.

## 6. Delivery lifecycle
```text
accept  -> PENDING (one row per channel)  -> deliver now -> SENT (provider, provider_message_id, sent_at)
                                                         -> FAILED (attempts+1, last_error)
retry   every 5 min: up to 100 FAILED rows with attempts < notification.retry.max-attempts (5), oldest first -> deliver again
```
Event types (`NotificationEventType`): REGISTRATION; QUOTE_GENERATED, QUOTE_EXPIRED; PROPOSAL_SUBMITTED / APPROVED /
REJECTED; PAYMENT_SUCCESS / FAILED / REFUNDED; POLICY_ISSUED / RENEWAL_REMINDER / EXPIRED / CANCELLED;
CLAIM_REGISTERED / DOCUMENTS_REQUIRED / APPROVED / REJECTED / SETTLED. Each has a subject, a plain-text e-mail body
and a short SMS body in `NotificationTemplates`; `firstName` is filled from customer-service when the producer did not send it.

## 7. Business rules
- Idempotent on `(idempotency_key, channel)`: a Feign retry, a Kafka redelivery or a scheduler re-run creates nothing
  new (`existsBy...` check plus the unique constraint as the race-safe backstop).
- Recipient: e-mail/phone from the request win; otherwise customer-service by `customerId`, then by `userId`. No e-mail
  address -> no EMAIL row. SMS only when `notification.sms-enabled` and a phone is known.
- Delivery exceptions are never swallowed by a provider: `NotificationService.deliver` marks the row FAILED, so the
  retry job owns recovery. After `max-attempts` the row stays FAILED for a human to look at.
- `SmtpEmailProvider` refuses to start without `notification.email.from`; Gmail rewrites the From to the account anyway
  unless it is a verified alias. The mail health indicator is disabled so `/actuator/health` does not open an SMTP
  connection on every probe.
- Producers publish only after their transaction commits (`NotificationPublisher`), so a rolled-back policy
  cancellation never produces a "policy cancelled" e-mail.

## 8. Communication
Inbound: HTTP from `FeignNotificationTransport` or Kafka from `KafkaNotificationTransport`, same payload, same `accept()`
(see `kafka-messaging-design.md` for topics, DLT and retries). Outbound: customer-service reads only.
Delivery to the outside world goes through the `ChannelProvider` port; a SES / SendGrid / Twilio adapter is one new
class selected by `notification.email.provider` / `notification.sms.provider`.

## 9. Security
`POST` is `hasRole('SERVICE')`: only other services, with the self-issued SERVICE token, can create notifications.
A customer lists only their own rows (filtered by the JWT user id); the reference lookup is staff only (403 otherwise).

## 10. Package structure
controller (NotificationController), service (NotificationService), client (CustomerClient, RecipientResolver),
template (NotificationTemplates, RenderedMessage), provider (ChannelProvider, MockEmailProvider, SmtpEmailProvider,
MockSmsProvider), messaging (NotificationRequestedListener, Kafka only), scheduler (NotificationRetryScheduler),
config (NotificationProperties), entity (Notification, Channel, DeliveryStatus), repository, dto, mapper.
