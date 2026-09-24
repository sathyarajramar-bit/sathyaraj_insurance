# Kafka messaging (Phase 4A)

Kafka is an **optional transport** in this platform. `KAFKA_ENABLED=false` (default) keeps every integration exactly
as before; `KAFKA_ENABLED=true` moves two integrations onto Kafka topics without touching any business code:

| Integration | HTTP (default) | Kafka (`KAFKA_ENABLED=true`) |
|---|---|---|
| payment-service -> policy-service, "premium collected, issue the policy" | outbox row -> `OutboxDispatcher` -> Feign `POST /api/policies/issue` | outbox row -> `OutboxDispatcher` -> `KafkaOutboxTransport` -> topic **`payment.succeeded`** -> `PaymentSucceededListener` (policy-service) |
| every service -> notification-service, "send an e-mail/SMS" | `NotificationPublisher` -> `FeignNotificationTransport` -> Feign `POST /api/notifications` | `NotificationPublisher` -> `KafkaNotificationTransport` -> topic **`notification.requested`** -> `NotificationRequestedListener` (notification-service) |

Both consumers call the *same* service methods as the HTTP endpoints (`PolicyIssuanceService.issue`,
`NotificationService.accept`), which were already idempotent - that is what makes at-least-once delivery safe.

## Why the outbox stays

Kafka does not remove the transactional outbox, it is the reason the outbox exists. A `KafkaTemplate.send()` inside
the payment transaction could be acknowledged by the broker and then the database transaction rolls back
(policy issued, payment not recorded), or the transaction commits and the process dies before the send. Writing the
event to `payment_outbox` in the same commit as the payment row and publishing *after* commit (with the retry
scheduler for the crash case) gives "the event exists if and only if the payment does". The only thing Kafka changed
is `OutboxTransport`: `deliver()` is now a producer send awaited with `acks=all`, and the row becomes DELIVERED
only after the broker acknowledged the record.

## Topics

| Topic | Key | Value (JSON) | Producer | Consumer group |
|---|---|---|---|---|
| `payment.succeeded` | paymentReference | `PaymentSuccessfulEvent` (= policy-service `IssuePolicyRequest`) | payment-service | `policy-service` |
| `notification.requested` | idempotencyKey (`POLICY_ISSUED:PL-1`) | `NotificationRequest` (common-lib) | auth, quote, proposal, payment, policy, claims | `notification-service` |
| `payment.succeeded.DLT`, `notification.requested.DLT` | same | same + `kafka_dlt-*` headers | the consumer's error handler | none (inspect with `scripts\kafka-cli.cmd dlt`) |

Topics are declared once, as `NewTopic` beans in `CommonKafkaAutoConfiguration` (common-lib), and created by
`KafkaAdmin` when the first service with `KAFKA_ENABLED=true` starts: 3 partitions, replication 1 (single broker).

**Keys and ordering.** Kafka orders records only within a partition, and a key always hashes to the same partition.
Keying by `paymentReference` / `idempotencyKey` means every record about one payment or one event lands on one
partition in order, while unrelated records spread over the 3 partitions and can be consumed in parallel
(`NotificationRequestedListener` runs `concurrency = 3`, one thread per partition).

## Producer settings (`spring.kafka.producer` in config-server `application.yml`)

| Setting | Value | Why |
|---|---|---|
| `acks` | `all` | the send completes only when the leader and all in-sync replicas wrote it; with 1 broker = the leader flushed it |
| `enable.idempotence` | `true` | the broker de-duplicates producer retries, so a network retry cannot create a duplicate record |
| `value-serializer` | `JsonSerializer`, `spring.json.add.type.headers=false` | plain JSON; consumers pick their own class instead of trusting a class name header from another service |
| `interceptor.classes` | `CorrelationIdProducerInterceptor` | copies the request's correlation id from the MDC into a `correlationId` header and stamps `source=<service>` |

`KafkaOutboxTransport` and `KafkaNotificationTransport` call `.get(10 s)` on the send future: a broker outage
becomes an exception (outbox retry with backoff / an error log line), never a record lost in the client buffer.

## Consumer settings (`spring.kafka.consumer` / `listener`)

| Setting | Value | Why |
|---|---|---|
| `group-id` | service name | one group per service: each service sees every record once per group; two instances of policy-service share the partitions |
| `auto-offset-reset` | `earliest` | a brand-new group (first deploy) processes what is already on the topic |
| `enable-auto-commit` | `false`, `listener.ack-mode=record` | the container commits the offset after the listener returned normally: a crash mid-record redelivers it (at-least-once) |
| value deserializer | `ErrorHandlingDeserializer` around `JsonDeserializer`, `spring.json.use.type.headers=false` | a corrupt record ("poison pill") becomes a `DeserializationException` handled by the error handler instead of an infinite crash loop; each `@KafkaListener` sets `spring.json.value.default.type` to its own DTO |

## Error handling and dead letters

`CommonKafkaAutoConfiguration` registers one `DefaultErrorHandler` for all listener containers:

1. transient failure (database down, customer-service 503): retry the record with exponential backoff
   `1 s, 2 s, 4 s` (`messaging.kafka.consumer.*`), blocking that partition only;
2. after `max-retries`, or immediately for a `BusinessException` (a proposal that is not APPROVED will not become
   APPROVED by retrying) and for a `DeserializationException`, `DeadLetterPublishingRecoverer` copies the record to
   `<topic>.DLT` on the same partition, with headers `kafka_dlt-exception-message`, `kafka_dlt-original-offset`, ...;
3. the offset is committed and the partition moves on.

The DLT is the operator's inbox: fix the cause, then replay with `kafka-console-producer` (or a small admin
endpoint later). Nothing consumes the DLTs automatically.

## Correlation id across a Kafka hop

HTTP: gateway `CorrelationIdFilter` -> MDC -> every log line. Kafka: `CorrelationIdProducerInterceptor` (MDC ->
header) and `CorrelationIdRecordInterceptor` (header -> MDC, cleared after the listener). Two thread hops needed care:
the outbox dispatch runs on an `@Async` thread and the notification publisher on the task executor, and neither
inherits the request thread's MDC - so `OutboxCommittedEvent` carries the correlation id and `NotificationPublisher`
copies the MDC into the executor task (`MDC.getCopyOfContextMap` / `setContextMap`), otherwise the producer
interceptor would mint a fresh id. One `grep <id>` still
shows the payment request in payment-service, the produced record, and the policy issued in policy-service.

## Security note

HTTP endpoints for these integrations are protected by the `SERVICE` role. Kafka records carry no JWT: whoever can
write to the topic can trigger a consumer. policy-service therefore still **re-reads the payment from payment-service**
(`PolicyIssuanceService.issueNew`) and refuses anything that is not a SUCCESS payment - a forged
`payment.succeeded` record cannot issue a policy. In production the broker would add TLS + SASL and per-topic ACLs
(payment-service write-only on `payment.succeeded`, policy-service read-only).

## Tests

| Module | Test | Proves |
|---|---|---|
| common-lib | `KafkaNotificationTransportTest` | record key = idempotency key; a failed send surfaces as an exception |
| payment-service | `KafkaOutboxIT` (embedded KRaft broker) | with Kafka on, a SUCCESS payment publishes `payment.succeeded` keyed by paymentReference with `correlationId`/`source` headers, the outbox row is DELIVERED and policy-service is **not** called over HTTP |
| policy-service | `PaymentSucceededListenerIT` | a record issues the policy; a redelivered record returns the same policy; a FAILED payment goes straight to `payment.succeeded.DLT` with the business message |
| notification-service | `NotificationRequestedListenerIT` | a record stores + sends EMAIL and SMS; a redelivery is ignored (idempotency key) |

`@EmbeddedKafka(kraft = true)` starts an in-process broker: no Docker, no local Kafka needed for `./mvnw test`.

## Running it locally (Windows, no Docker)

```
scripts\kafka-start.cmd                  # first run formats C:\Softwares\kafka\kafka_2.13-4.3.1\data, then starts on localhost:9092
set KAFKA_ENABLED=true                   # in every service terminal (or once in the parent shell)
java -jar payment-service\target\... / policy-service ... / notification-service ... / the rest
scripts\kafka-cli.cmd topics             # payment.succeeded, notification.requested, *.DLT
scripts\kafka-cli.cmd groups             # consumer lag of policy-service / notification-service
scripts\kafka-cli.cmd tail payment.succeeded
```

## Windows notes (learned the hard way)

- `binwindowskafka-run-class.bat` adds ~130 jars to the classpath one by one; from an install path like
  `C:Softwareskafkakafka_2.13-4.3.1` the expanded command line passes cmd.exe's 8191-character limit and every tool
  dies with *"The syntax of the command is incorrect."* `scripts\kafka-patch-classpath.ps1` (run by `kafka-start.cmd`)
  replaces that loop with a single `libs*` wildcard entry; the original is kept as `kafka-run-class.bat.orig`.
- `kafka-server-start.bat` and `kafka-server-stop.bat` call `wmic`, which Windows 11 no longer ships. The start script
  sets `KAFKA_HEAP_OPTS` (skips the probe) and `kafka-stop.cmd` finds the broker JVM with PowerShell instead.
- Kafka cannot delete memory-mapped log segments on Windows while they are open, and a failed delete crashes the broker.
  `local-kraft.properties` sets 7-day retention and 1 GB segments so the cleaner never runs during a dev session; if the
  broker refuses to start after a crash, delete `%KAFKA_HOME%data` (the script formats it again).
- Serialised JSON: `CommonKafkaAutoConfiguration` gives the producer factory a `JsonSerializer` built on Boot's
  `ObjectMapper` so `Instant`s are ISO-8601 strings, exactly like the REST responses (spring-kafka's default mapper
  writes numeric timestamps).

## Interview one-liners

- *Why not call policy-service over Kafka from inside the payment transaction?* Dual-write problem -> outbox.
- *Exactly-once?* Kafka gives at-least-once here (idempotent producer + offsets committed after processing);
  the exactly-once *effect* comes from idempotent consumers (`policies.payment_reference` unique,
  `notifications(idempotency_key, channel)` unique).
- *What happens when policy-service is down?* Records wait on the topic; the consumer group resumes from its last
  committed offset. With HTTP the outbox retry scheduler did the same job.
- *Ordering?* Per key/partition only; keys are chosen so ordering matters only within one payment / one event.
- *Poison pill?* `ErrorHandlingDeserializer` + DLT, otherwise the partition would be stuck forever.
