# Platform design (Phase 1 + common-lib): eureka-server, config-server, api-gateway, common-lib

## 1. Responsibility
The four modules every business service depends on. None of them holds business logic.

| module | what it owns |
|---|---|
| eureka-server | the registry: which instances of which service are alive and where |
| config-server | every service's configuration, served over HTTP from one folder |
| api-gateway | the single public URL; routing, JWT authentication, correlation id, request log, standard errors |
| common-lib | a library (not a service): error format, JWT verification, auditing, Feign interceptor, notification publisher, Kafka wiring |

## 2. Architecture
```text
                 +-------------------------+   fetch config at start-up (HTTP Basic)
client --JWT-->  | api-gateway :8080       |<--------------------------------- config-server :8888
                 |  CorrelationIdFilter    |                                   serves classpath:/config/*.yml
                 |  RequestLoggingFilter   |
                 |  JwtAuthenticationFilter|   lb://<service>  --> eureka-server :8761 (who is alive?)
                 |  GatewayExceptionHandler|
                 +-----------+-------------+
                             | X-User-Id / X-User-Email / X-User-Roles / X-Correlation-Id + original JWT
                             v
                 business service (servlet, common-lib auto-configured)
                   CorrelationIdFilter (MDC) -> JwtAuthenticationFilter (re-verifies JWT) -> controller
                   Feign -> other service   (SERVICE JWT + correlation id added by CommonFeignAutoConfiguration)
```
Start-up order: eureka-server, config-server, api-gateway, then business services in any order. Every
client has `spring.config.import: optional:configserver:${CONFIG_SERVER_URL}`; `CONFIG_FAIL_FAST=true`
(docker-compose) refuses to start without configuration, the laptop default starts with local values.

## 3. eureka-server (port 8761)
Standalone registry (`register-with-eureka: false`, `fetch-registry: false`). Clients renew their lease every
10 s and are evicted after 30 s without a heartbeat (values in the shared `application.yml`); the server scans
every 10 s and refreshes its response cache every 5 s. Self-preservation is off locally
(`EUREKA_SELF_PRESERVATION`) because on a laptop it only hides dead instances. Instances register as
`localhost` by default; Docker sets `EUREKA_PREFER_IP=true`. Dashboard: http://localhost:8761.

## 4. config-server (port 8888)
Profile `native`: serves `config-server/src/main/resources/config/` (or `CONFIG_REPO_URI=file:///...` to edit
without rebuilding) behind HTTP Basic (`config-user` / `config-pass`, overridable). Resolution order a service
sees, lowest precedence first:
```text
application.yml        shared by everyone: Eureka, actuator, JPA defaults, Kafka, security.jwt, logging pattern, messaging
application-h2.yml     profile h2: run without MySQL
<service>.yml          port, datasource, flyway history table, service-specific settings, public paths
```
Secrets are `${ENV:default}` placeholders resolved on the client side, so the files never hold a real
secret. Check what a service receives with `curl -u config-user:config-pass localhost:8888/auth-service/default`.

## 5. api-gateway (port 8080)
Spring Cloud Gateway on WebFlux. Routes live in `config/api-gateway.yml`, one block per service, the path is
never rewritten:
```text
/api/auth/**  /api/customers/**  /api/products/**  /api/quotes/**  /api/proposals/**
/api/payments/**  /api/policies/**  /api/notifications/**  /api/claims/**  /api/documents/**
      +--> lb://<service-name>   (Eureka lookup + client-side load balancing)
```
Filters, in order:
1. `CorrelationIdFilter` (WebFilter, highest precedence): reuse or generate `X-Correlation-Id`, forward it,
   echo it on the response. A WebFilter so even 404s and actuator calls get one.
2. `RequestLoggingFilter`: one INFO line per request (method, path, status, ms, correlation id); actuator at DEBUG.
3. `JwtAuthenticationFilter` (GlobalFilter): OPTIONS and `gateway.security.public-paths` pass through;
   otherwise `Authorization: Bearer` is required, the HS256 signature and expiry are checked by
   `JwtTokenValidator`, and the identity is forwarded as `X-User-Id`, `X-User-Email`, `X-User-Roles`.
   Client-supplied copies of those headers are always stripped first (anti-spoofing).
4. `GatewayExceptionHandler` (order -2): no route -> 404 NOT_FOUND, no live instance -> 503 SERVICE_UNAVAILABLE,
   connect failure -> 503, `response-timeout` (10 s) -> 504 GATEWAY_TIMEOUT, else 500 INTERNAL_ERROR, all in the
   platform error body. Downstream errors are proxied untouched.

CORS for `CORS_ALLOWED_ORIGINS` (localhost:3000 / 4200 by default) with credentials; `/actuator/gateway/routes`
is open for debugging. `GatewaySecurityProperties` fails start-up when the secret is shorter than 32 chars.

## 6. common-lib (the platform starter)
Auto-configured through `META-INF/spring/...AutoConfiguration.imports`; services never component-scan it.
Every bean is `@ConditionalOnMissingBean`, so a service can override any piece by declaring its own.
```text
web           CommonWebAutoConfiguration: GlobalExceptionHandler, CorrelationIdFilter (MDC key for the log pattern), OpenApiConfig (bearerAuth)
security      CommonSecurityAutoConfiguration: JwtProperties (security.jwt.*), JwtTokenVerifier (requires iss insurance-platform),
              JwtAuthenticationFilter -> AuthenticatedUser principal + ROLE_* authorities, JsonSecurityErrorHandlers (401/403 JSON),
              stateless SecurityFilterChain (health, info, OpenAPI, swagger + security.public-paths permitAll, rest authenticated),
              @EnableMethodSecurity (@PreAuthorize), BCrypt PasswordEncoder, ServiceTokenProvider, CurrentUser helper
jpa           CommonJpaAutoConfiguration: JPA auditing; AuditableEntity (created_at/by, updated_at/by, @Version);
              auditor = user id, the service name for SERVICE tokens, "system" for jobs
client        CommonFeignAutoConfiguration: RequestInterceptor adding "Bearer <SERVICE JWT>" and X-Correlation-Id to every Feign call
notification  NotificationPublisher (after-commit, async, MDC restored), NotificationRequest, NotificationEventType,
              NotificationTransport: FeignNotificationTransport (default) or KafkaNotificationTransport
messaging     CommonKafkaAutoConfiguration (only with messaging.kafka.enabled=true): topics, error handler + DLT,
              correlation-id producer/record interceptors, MessagingProperties  (see kafka-messaging-design.md)
dto           ApiErrorResponse (same shape as the gateway), FieldErrorDetail, PageResponse<T>
exception     BusinessException base; ResourceNotFound 404, DuplicateResource 409, Validation 400, Forbidden 403,
              ServiceUnavailable 503, Payment, QuoteExpired, Policy
```
`GlobalExceptionHandler` maps: BusinessException -> its status/code; bean validation -> 400 VALIDATION_ERROR with
field errors; unreadable body / type mismatch -> 400 MALFORMED_REQUEST; AuthenticationException -> 401;
AccessDeniedException -> 403 (401 when anonymous); optimistic lock -> 409 CONCURRENT_MODIFICATION; unique
constraint -> 409 DATA_INTEGRITY_VIOLATION; unknown path -> 404; wrong method -> 405; anything else -> 500 with
details only in the log.

## 7. Security model (end to end)
```text
token    HS256, secret security.jwt.secret (same value as gateway.security.jwt-secret), iss insurance-platform,
         sub = user id, email, roles [CUSTOMER|AGENT|ADMIN|CLAIMS_HANDLER], exp = iat + 15 min
service  ServiceTokenProvider: sub 0, roles [SERVICE], 5 min lifetime, cached and renewed after 4 min
where    authentication once at the gateway, again in every service (defence in depth: a call that skips the
         gateway inside the network is still rejected); authorization in the service (@PreAuthorize + access
         policy classes), because ownership rules need the service's own data
public   listed twice on purpose: gateway.security.public-paths and each service's security.public-paths
```

## 8. Communication
Sync: Feign through Eureka, never through the gateway, with the SERVICE token and the correlation id;
Resilience4j circuit breaker + retry in each service's gateway classes, 503 naming the dependency as fallback.
Async: `NotificationPublisher` (all services -> notification-service) and the payment outbox
(payment-service -> policy-service), both over HTTP by default and over Kafka with `KAFKA_ENABLED=true`.

## 9. Decisions
- One shared library versus copy-paste per service: copying keeps services independent but the error
  format and security rules drift within weeks. A thin, versioned library with zero domain code is the
  compromise; business logic appearing in it is the signal to split it.
- HS256 shared secret versus RS256: HS256 is simpler, but every verifier could also forge tokens.
  Swapping `JwtTokenIssuer` / `JwtTokenVerifier` / `JwtTokenValidator` to a key pair is a local change.
- Correlation id instead of tracing: good enough to grep one request across logs; Micrometer Tracing +
  OpenTelemetry replaces it with `traceparent`, spans and a UI when needed.
- WebFlux gateway in a servlet platform: the gateway is I/O bound and holds many idle connections, which
  Netty's event loop handles with a handful of threads.

## 10. Package structure
```text
eureka-server   EurekaServerApplication only (configuration in application.yml)
config-server   ConfigServerApplication + resources/config/*.yml (the actual platform configuration)
api-gateway     config (GatewaySecurityProperties, SecurityBeansConfig), filter (CorrelationIdFilter, RequestLoggingFilter,
                GatewayHeaders), security (JwtAuthenticationFilter, JwtTokenValidator, PublicPathMatcher, AuthenticatedUser),
                exception (GatewayExceptionHandler, ErrorResponseWriter, GatewayErrorResponse)
common-lib      web, security, jpa, client, notification, messaging, dto, exception
```
