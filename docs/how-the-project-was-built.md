# How this project was built, step by step

This is the "if I were creating it again from an empty folder" story of `C:\Users\sathyaraj.ramar\insurance-ecommerce`.
Everything below refers to files that really exist in the repo; nothing is assumed.

---

## Step 0 - The decision before any code

The goal was a motor-insurance e-commerce platform that behaves like a real microservice system (many independently
deployable services) but stays learnable on one laptop.

Three choices were made before the first file:

| Decision | Choice | Why |
|---|---|---|
| Repository layout | **one Git repo, one Maven multi-module build** (a "monorepo") | one `git clone`, one `./mvnw install` builds 14 modules in the right order; shared versions are pinned once; still every module is its own Spring Boot app with its own jar |
| Build tool | Maven with the wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` -> Maven 3.9.12) | nobody needs Maven installed; the wrapper downloads the pinned version |
| Runtime split | platform modules (Eureka, Config Server, Gateway) + one shared library + one service per business capability | matches how a real team would split ownership: auth, customer, product, quote, proposal, payment, policy, claims, document, notification |

**Why multi-module instead of 14 separate repos?** Same reason most teams start this way: refactoring across services
is one commit, versions never drift (one parent POM), and a single CI job proves everything compiles and passes. The
services do *not* share code at runtime except through `common-lib` (a versioned jar) and HTTP/Kafka contracts, so any
module could be moved to its own repo later without code changes.

**Why not one Spring Boot project?** See Step 22 at the end - the short version: one process cannot demonstrate service
discovery, an API gateway, per-service data ownership, independent scaling, an outbox between two services, or a broker
between them, and those are exactly the things this project exists to teach.

---

## Step 1 - Project creation: the root folder and the parent POM

The very first thing created was the root folder and its `pom.xml` (the **parent POM**). Nothing else can exist before
it, because every module points at it as its `<parent>`.

Root folder contents today:

```
insurance-ecommerce/
  pom.xml                 <- parent POM (Step 2)
  mvnw, mvnw.cmd, .mvn/   <- Maven wrapper
  README.md               <- living documentation
  .env.example            <- every environment variable the platform reads, with defaults
  docker-compose.yml      <- MySQL, Redis, Kafka + all 13 apps as containers (unverified here: no Docker)
  docker/mysql/init.sql   <- per-service databases for the compose setup
  scripts/                <- run-local.sh, kafka-start.cmd, kafka-stop.cmd, kafka-cli.cmd, kafka-patch-classpath.ps1
  docs/                   <- design notes (product, quote, proposal, kafka, this file)
  logs/                   <- runtime logs when started by the scripts (git-ignored)
  eureka-server/ config-server/ api-gateway/          <- platform modules
  common-lib/                                          <- shared starter library
  auth-service/ customer-service/ product-service/ quote-service/ proposal-service/
  payment-service/ policy-service/ claims-service/ document-service/ notification-service/   <- business services
```

Every module has the same inner shape (Maven standard layout):

```
<module>/
  pom.xml
  src/main/java/com/insurance/<module>/...      <- code, package root com.insurance.<short name>
  src/main/resources/application.yml            <- bootstrap config only (name, port, where the Config Server is)
  src/main/resources/db/migration/V1__*.sql     <- Flyway schema (business services only)
  src/test/java/...                             <- unit tests (*Test) and Spring Boot integration tests (*IT)
  src/test/resources/application-test.yml       <- H2 in MySQL mode, Eureka off, config server off
  Dockerfile
```

---

## Step 2 - The parent `pom.xml`, line by line

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.16</version>
    <relativePath/>
</parent>
```
Our parent's own parent is Spring Boot's starter parent. That one thing gives us: the **Spring Boot BOM** (hundreds of
library versions pinned and tested together - we never write a version for `spring-boot-starter-web`, Jackson, Hibernate,
Flyway, MySQL driver, H2, Testcontainers, spring-kafka...), sensible compiler/resource plugin defaults, and the
`${lombok.version}` property we reuse later. `<relativePath/>` (empty) tells Maven "do not look for this parent on disk,
fetch it from the repository".

```xml
<groupId>com.insurance</groupId>
<artifactId>insurance-ecommerce</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>pom</packaging>
```
Our coordinates. `packaging=pom` means this module produces no jar; it only aggregates the children and holds shared
configuration. `-SNAPSHOT` = "in development"; every child inherits this version (`${project.version}`).

```xml
<modules>
    <module>eureka-server</module>
    <module>config-server</module>
    <module>api-gateway</module>
    <module>common-lib</module>
    <module>auth-service</module>
    ... (one line per service, 14 in total)
</modules>
```
The **reactor list**: which sub-folders Maven builds when you run a command at the root. The order here is a hint only;
Maven re-orders by dependencies (Step 19).

```xml
<properties>
    <java.version>17</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <spring-cloud.version>2025.0.3</spring-cloud.version>
    <jjwt.version>0.12.6</jjwt.version>
    <springdoc.version>2.8.9</springdoc.version>
    <mapstruct.version>1.6.3</mapstruct.version>
    <lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>
</properties>
```
`java.version` is read by Boot's parent to set `--release 17`. The other four are the only libraries whose versions Spring
Boot does *not* manage, so we pin them once here. `spring-cloud.version` must match the Boot line
(2025.0.x <-> Boot 3.5.x) - mixing trains is the number-one cause of "NoSuchMethodError" in Spring Cloud projects.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency> jjwt-api / jjwt-impl / jjwt-jackson  ${jjwt.version} </dependency>
        <dependency> springdoc-openapi-starter-webmvc-ui / -webflux-ui  ${springdoc.version} </dependency>
        <dependency> mapstruct  ${mapstruct.version} </dependency>
    </dependencies>
</dependencyManagement>
```
`dependencyManagement` does **not** add anything to any module. It says: "*if* a child declares this artifact, use this
version". The Spring Cloud entry is a BOM import (`type=pom, scope=import`), which pulls in the versions of every
Spring Cloud starter (Eureka, Config, Gateway, OpenFeign, Resilience4j...). That is why no child POM contains a single
`<version>` for a Spring artifact.

```xml
<dependencies>
    <dependency> lombok  <optional>true</optional> </dependency>
    <dependency> spring-boot-starter-test  <scope>test</scope> </dependency>
</dependencies>
```
These two *are* inherited by every module: Lombok (compile-time only, `optional` so it is not passed on to consumers of
`common-lib`) and the test starter (JUnit 5, AssertJ, Mockito, Awaitility, MockMvc). Deliberately nothing else - no
JPA, no web - so the gateway (reactive) and the library never get servlet or database dependencies they did not ask for.

```xml
<build><pluginManagement><plugins>
    <plugin> spring-boot-maven-plugin
        excludes lombok from the fat jar; executions: repackage + build-info </plugin>
    <plugin> maven-surefire-plugin
        includes **/*Test.java, **/*Tests.java, **/*IT.java </plugin>
    <plugin> maven-compiler-plugin
        <parameters>true</parameters>
        annotationProcessorPaths: lombok, mapstruct-processor, lombok-mapstruct-binding </plugin>
</plugins></pluginManagement></build>
```
`pluginManagement` = configuration that applies *only if a child declares the plugin*. The Boot plugin turns a module's
plain jar into an executable "fat jar" (`repackage`) and writes `build-info.properties` for `/actuator/info`; `common-lib`
does not declare it, so it stays a normal library jar. Surefire is told to run `*IT` classes in the ordinary `mvn test`
phase (they use H2, so they need no infrastructure). The compiler plugin wires the three annotation processors in the
right order (Lombok first, then MapStruct, with the binding so MapStruct sees Lombok-generated getters) and keeps
parameter names (`-parameters`) so Spring can bind `@PathVariable`/`@RequestParam` without explicit names.

---

## Step 3 - The complete structure and what each part owns

| Module | Port | Kind | Responsibility |
|---|---|---|---|
| `eureka-server` | 8761 | platform | service registry: every app registers its name + host:port and renews a lease; clients ask "where is quote-service?" |
| `config-server` | 8888 | platform | serves `config/application.yml` (shared) + `config/<service>.yml` to every app at startup, behind HTTP Basic |
| `api-gateway` | 8080 | platform | single public entry: routes `/api/x/**` to `lb://x-service`, validates the JWT, adds `X-User-*` and `X-Correlation-Id`, uniform JSON errors |
| `common-lib` | - | library | auto-configured starter: error body + exceptions, JWT resource-server security, JPA auditing, correlation-id filter, OpenAPI defaults, service-to-service Feign token, notification publisher, Kafka plumbing |
| `auth-service` | 8081 | business | accounts, login, JWT issuing, refresh-token rotation, roles |
| `customer-service` | 8082 | business | customer profile, KYC, vehicles |
| `product-service` | 8083 | business | product catalogue, coverages, add-ons, eligibility rules, pricing config (Redis cache) |
| `quote-service` | 8084 | business | premium calculation, quote lifecycle (Redis cache, expiry job) |
| `proposal-service` | 8085 | business | quote -> proposal, submit/review/approve/reject with history |
| `payment-service` | 8086 | business | charge the premium (provider port), transactional outbox -> policy |
| `policy-service` | 8087 | business | issue/renew/cancel/expire policies, policy document, reminders |
| `claims-service` | 8090 | business | claim lifecycle against an active policy, evidence links |
| `document-service` | 8089 | business | file upload/download, generated documents, verification |
| `notification-service` | 8088 | business | e-mail/SMS records, providers (mock/SMTP), retry job |

Packages inside a business service always mean the same thing:

| Package | Contains | Rule |
|---|---|---|
| `controller` | `@RestController` classes, one per resource | only HTTP concerns: validation, status codes, `@PreAuthorize` |
| `dto` | request/response records | never expose entities |
| `service` | business rules, `@Transactional` boundaries | the only place that calls repositories and clients |
| `entity` | JPA entities + status enums | extend `AuditableEntity` from common-lib |
| `repository` | Spring Data interfaces (+ `*Specifications` for optional filters) | no logic |
| `mapper` | MapStruct interfaces entity <-> dto | generated at compile time |
| `client` | Feign interfaces + `*Gateway` wrappers (Resilience4j) | the only place that knows another service exists |
| `config` | `@ConfigurationProperties`, cache config, bootstrap beans | |
| `security` | service-specific access policies (`CustomerAccessPolicy`, `JwtTokenIssuer`) | |
| `scheduler` / `event` / `messaging` | cron jobs, domain events, Kafka listeners | present only where needed |

---

## Step 4 - First child module: `eureka-server`

**Why first.** Nothing else can be *found* without it. Every later module registers here, so it is the first thing you
start and the first thing that was written.

**Created:** folder `eureka-server/`, its `pom.xml`, `src/main/java/com/insurance/eureka/EurekaServerApplication.java`,
`src/main/resources/application.yml`.

`pom.xml`:
```xml
<parent> com.insurance : insurance-ecommerce : 1.0.0-SNAPSHOT  <relativePath>../pom.xml</relativePath> </parent>
<artifactId>eureka-server</artifactId>
```
Every child starts with this block: "my parent is the root POM, one directory up". `groupId` and `version` are inherited.

```xml
<dependency> spring-cloud-starter-netflix-eureka-server </dependency>   <!-- the registry itself (web server included) -->
<dependency> spring-boot-starter-actuator </dependency>                  <!-- /actuator/health for scripts and Docker -->
<build><plugins><plugin> spring-boot-maven-plugin </plugin></plugins></build>   <!-- opt in: build an executable jar -->
```
No versions anywhere: the Spring Cloud BOM from Step 2 supplies them.

Code: one class, `@SpringBootApplication @EnableEurekaServer public class EurekaServerApplication { main -> SpringApplication.run }`.

`application.yml` (this module has no Config Server to talk to, so everything is local):
- `spring.application.name: eureka-server`, `server.port: ${SERVER_PORT:8761}`
- `eureka.client.register-with-eureka: false` and `fetch-registry: false` - a standalone registry must not register with itself
- `eureka.server.enable-self-preservation: false` for laptops (otherwise dead instances linger), `eviction-interval-timer-in-ms: 10000`

Verify: `http://localhost:8761` shows the dashboard.

---

## Step 5 - `config-server`

**Why.** Ten services x (datasource, JWT secret, Feign timeouts, Resilience4j, Kafka...) would be ten copies of the same
YAML. The Config Server keeps all of it in **one folder** and every service pulls its slice at startup; change a value,
restart the service, done.

`pom.xml` dependencies:
```xml
spring-cloud-config-server                 <!-- the server -->
spring-cloud-starter-netflix-eureka-client <!-- registers in Eureka (visible on the dashboard) -->
spring-boot-starter-security               <!-- HTTP Basic: the config contains secrets and topology -->
spring-boot-starter-actuator
+ spring-boot-maven-plugin
```

Code: `ConfigServerApplication` (`@EnableConfigServer`) and `config/SecurityConfig.java` - one `SecurityFilterChain`:
`/actuator/health` is public, everything else needs Basic auth (`config-user`/`config-pass` by default).

`src/main/resources/application.yml`: `spring.profiles.include: native` (serve files instead of a Git repo),
`spring.cloud.config.server.native.search-locations: ${CONFIG_REPO_URI:classpath:/config}` (override with a `file:///`
URL to edit config without rebuilding), `spring.security.user.name/password` from env vars.

`src/main/resources/config/` - the heart of the platform's configuration:
- `application.yml` - shared by every client: Eureka settings, actuator exposure, JPA/Flyway defaults (`ddl-auto: validate`,
  `baseline-version: 0`), the JWT secret/issuer/TTLs, log pattern with the correlation id, `spring.kafka.*` and
  `messaging.kafka.*`.
- `application-h2.yml` - profile `h2`: run without MySQL.
- `<service>.yml` x 11 - port, datasource URL, Flyway history table name, public paths, Resilience4j instances,
  service-specific properties (`payment.outbox.*`, `policy.renewal-window-days`, `notification.email.provider`...),
  and for the gateway the whole route table.

How a client uses it: `GET http://localhost:8888/auth-service/default` (Basic auth) returns `auth-service.yml` merged
over `application.yml`. Placeholders such as `${MYSQL_HOST:localhost}` are resolved on the **client** side, so each
container/laptop supplies its own values.

---

## Step 6 - `api-gateway`

**Why.** Clients should know one URL, not ten. The gateway also does the security work once at the edge.

`pom.xml` dependencies and the reason for each:
```xml
spring-cloud-starter-gateway-server-webflux   <!-- reactive gateway (Netty). Must NOT have spring-boot-starter-web -->
spring-cloud-starter-netflix-eureka-client    <!-- resolves lb://quote-service through Eureka -->
spring-cloud-starter-config                   <!-- pulls routes + security settings from the Config Server -->
spring-retry + spring-boot-starter-aop        <!-- spring.cloud.config.retry.* (wait for the Config Server at boot) -->
spring-boot-starter-validation                <!-- validates @ConfigurationProperties (JWT secret must be present) -->
spring-boot-starter-actuator
jjwt-api (compile), jjwt-impl + jjwt-jackson (runtime)   <!-- parse and verify the HS256 token -->
reactor-test (test)
```
Note: the gateway does **not** depend on `common-lib` - the library is servlet/JPA based and the gateway is reactive.
It has its own small JWT validator instead.

Packages (`com.insurance.gateway`):
- `filter/CorrelationIdFilter` (reads or creates `X-Correlation-Id`, echoes it back), `filter/RequestLoggingFilter`,
  `filter/GatewayHeaders` (the `X-User-Id/Email/Roles` names)
- `security/JwtAuthenticationFilter` (global filter: public path? else validate token, strip spoofed `X-User-*`, set real
  ones), `security/JwtTokenValidator`, `security/PublicPathMatcher`, `security/AuthenticatedUser`, `security/InvalidTokenException`
- `config/GatewaySecurityProperties` (`gateway.security.*` from the Config Server), `config/SecurityBeansConfig`
- `exception/GatewayExceptionHandler` + `ErrorResponseWriter` + `GatewayErrorResponse`: 401/404/503/504 as the same JSON
  body the services use `{timestamp,status,error,message,path}`

`src/main/resources/application.yml` is *bootstrap only*: `spring.application.name`, `spring.config.import:
"optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}"`, Basic credentials, retry, Eureka URL. The route
table lives in `config-server/.../config/api-gateway.yml`: one route per service,
`uri: lb://<service>`, `predicates: Path=/api/<resource>/**`, plus CORS and timeouts.

---

## Step 7 - `common-lib`, the platform starter

**Why.** By the time the second business service existed, the error format, the JWT filter, the auditing columns and
the correlation-id logging were about to be copied. A copy drifts within weeks; a small **versioned library with zero
domain code** does not. If it ever grows business logic, that is the signal to split it.

`pom.xml` (no Boot plugin: it is a plain jar):
```xml
spring-boot-starter-web, -validation, -security, -data-jpa, -actuator   <!-- what every business service needs anyway -->
springdoc-openapi-starter-webmvc-ui                                     <!-- Swagger UI + bearer-auth defaults -->
jjwt-api (compile), jjwt-impl + jjwt-jackson (runtime)                 <!-- verify tokens issued by auth-service -->
spring-kafka                                                           <!-- producer/consumer support (Phase 4A) -->
spring-cloud-starter-openfeign  <optional>true</optional>              <!-- only services that call others get Feign -->
spring-security-test (test)
```
Because these are *compile* dependencies of the library, every service that depends on `common-lib` transitively gets
web, JPA, security, actuator, springdoc and Kafka - which is exactly why the service POMs are so short.

**How it plugs itself in: Spring Boot auto-configuration, not component scanning.** Services have package root
`com.insurance.<svc>`, so `@SpringBootApplication` never scans `com.insurance.common`. Instead
`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` lists six classes:

| Auto-configuration | Package | Registers |
|---|---|---|
| `CommonWebAutoConfiguration` | `web` | `CorrelationIdFilter` (MDC key `correlationId`), OpenAPI bearer scheme |
| `CommonSecurityAutoConfiguration` | `security` | `JwtProperties` (`security.jwt.*`), `JwtTokenVerifier`, `JwtAuthenticationFilter`, JSON 401/403 handlers, a default stateless `SecurityFilterChain` (public paths from `security.public-paths`, `"GET /path"` method-aware), `@EnableMethodSecurity`, `PasswordEncoder` |
| `CommonJpaAutoConfiguration` | `jpa` | `@EnableJpaAuditing` with `platformAuditorAware` (JWT user id, `SERVICE` e-mail, or `system`) |
| `CommonFeignAutoConfiguration` | `client` | `ServiceTokenProvider` (self-issued `SERVICE` JWT) + Feign `RequestInterceptor` adding it and the correlation id |
| `CommonNotificationAutoConfiguration` | `notification` | `NotificationClient` (Feign), `NotificationTransport`, `NotificationPublisher` (after-commit, async) |
| `CommonKafkaAutoConfiguration` | `messaging` | topics, error handler + DLT, correlation-id interceptors, Kafka notification transport - only with `messaging.kafka.enabled=true` |

Every bean is `@ConditionalOnMissingBean`, so a service can override any default by declaring its own bean.
Also in the library: `dto/ApiErrorResponse, FieldErrorDetail, PageResponse`, `exception/BusinessException` and its
subclasses + `GlobalExceptionHandler` (`@RestControllerAdvice` mapping each exception to a status), `jpa/AuditableEntity`
(`created_at/by, updated_at/by, @Version`), `security/CurrentUser` and `AuthenticatedUser`.

---

## Step 8 - `auth-service`: the template for every business service

**Why first among the services.** Nothing else can be called without a token.

### Its `pom.xml`, line by line (all later services follow the same pattern)

```xml
<dependency>
    <groupId>com.insurance</groupId>
    <artifactId>common-lib</artifactId>
    <version>${project.version}</version>
</dependency>
```
The platform starter (Step 7). `${project.version}` = the same `1.0.0-SNAPSHOT`, so library and service always move together.

```xml
<dependency> com.mysql : mysql-connector-j  <scope>runtime</scope> </dependency>
```
JDBC driver. `runtime` scope: code never imports it, only Hikari loads it.

```xml
<dependency> org.flywaydb : flyway-core </dependency>
<dependency> org.flywaydb : flyway-mysql </dependency>
```
Schema migrations. Flyway runs `db/migration/V1__create_auth_tables.sql` at startup; Hibernate only *validates* the
mapping (`ddl-auto: validate` in the shared config). `flyway-mysql` is the MySQL dialect module (required since Flyway 10).

```xml
<dependency> com.h2database : h2  <scope>runtime</scope> </dependency>
```
In-memory database for tests (`application-test.yml`, `MODE=MySQL`) and for the `h2` profile on a laptop without MySQL.

```xml
<dependency> spring-cloud-starter-netflix-eureka-client </dependency>
<dependency> spring-cloud-starter-config </dependency>
<dependency> spring-cloud-starter-openfeign </dependency>
```
Register with Eureka; pull configuration from the Config Server; call customer-service at registration. Services that
never call another service (customer, product, document) omit OpenFeign.

```xml
<dependency> org.mapstruct : mapstruct </dependency>
```
The annotation API; the processor comes from the parent's compiler configuration.

```xml
spring-security-test (test)                 <!-- MockMvc with JWTs -->
org.testcontainers : junit-jupiter, mysql (test)   <!-- AuthFlywayMySqlIT against a real MySQL; skipped without Docker -->
+ spring-boot-maven-plugin                  <!-- executable jar -->
```

### What was created inside

```
com.insurance.auth
  AuthServiceApplication        @SpringBootApplication @EnableFeignClients
  controller/AuthController      POST /api/auth/register|login|refresh|logout, GET /me
  controller/UserAdminController GET /api/auth/users, PUT /{id}/roles (ADMIN)
  dto/RegisterRequest, LoginRequest, RefreshTokenRequest, AssignRolesRequest, AuthResponse, UserResponse
  service/AuthService            register, login, refresh, logout (transaction boundaries)
  service/RefreshTokenService    issue / rotate / revoke, hashed at rest
  service/UserService            admin search, role assignment
  security/JwtTokenIssuer        builds the HS256 access token (sub=userId, email, roles)
  security/InvalidCredentialsException
  entity/User (users), Role, UserStatus, RefreshToken (refresh_tokens)
  repository/UserRepository, RefreshTokenRepository
  mapper/UserMapper
  client/CustomerClient (@FeignClient "customer-service"), CustomerProfileClient (anti-corruption wrapper),
         CreateCustomerRequest, CustomerResponse
  config/AdminBootstrapProperties, AdminUserInitializer   (first ADMIN from ADMIN_EMAIL/ADMIN_PASSWORD)
resources/application.yml       name, config import, Eureka URL, port 8081
resources/db/migration/V1__create_auth_tables.sql   users, user_roles, refresh_tokens
```
Its served configuration is `config-server/.../config/auth-service.yml`: port, datasource (`retail_db`),
`spring.flyway.table: flyway_history_auth_service`, `security.public-paths` (register/login/refresh), `auth.admin.*`.

**Communicates with:** customer-service (Feign, `POST /api/customers`, SERVICE token) and notification-service
(`NotificationPublisher`, REGISTRATION). Everything else calls *it* only indirectly: they verify the tokens it issued.

---

## Step 9 - `customer-service`

Same POM as auth minus OpenFeign (it calls nobody). Owns `customers` (with an embedded `Address` value object) and
`vehicles`. `security/CustomerAccessPolicy` is the ownership rule reused by every endpoint: a CUSTOMER sees only their
own rows, ADMIN/AGENT everything, SERVICE may read. `repository/CustomerSpecifications` builds optional filters for the
admin search. Called by: auth (create profile), quote (customer + vehicle), proposal (KYC status), notification
(recipient e-mail/phone).

## Step 10 - `product-service`

POM adds `spring-boot-starter-cache` + `spring-boot-starter-data-redis` (+ `testcontainers-redis`). Owns the catalogue
aggregate (`products`, `product_coverages`, `product_add_ons`, `product_eligibility_rules`, `product_pricing`), seeded by
`V2`/`V3` migrations. `@EnableCaching` on the application class; caches `products`, `product-catalog`, `product-pricing`
with a `CacheErrorHandler` so a Redis outage degrades to database reads. Called by quote-service (eligibility, pricing).

## Step 11 - `quote-service`

POM adds Redis/cache **and** `spring-cloud-starter-openfeign`, `spring-cloud-starter-circuitbreaker-resilience4j`,
`resilience4j-spring-boot3`, `spring-boot-starter-aop` (annotations need proxies). First service with a `client`
package that has both Feign interfaces (`CustomerClient`, `ProductClient`) and gateways (`CustomerGateway`,
`ProductCatalogGateway`) carrying `@CircuitBreaker` + `@Retry` with a 503 fallback. `pricing/PremiumCalculator` is a pure
class (unit-tested to the paisa), `scheduler/QuoteExpiryScheduler` (cron), `event/QuoteGeneratedEvent` + publisher.
Application class: `@EnableFeignClients @EnableCaching @EnableScheduling @EnableAsync`.

## Step 12 - `proposal-service`

Feign + Resilience4j like quote; no Redis. Owns `proposals` + `proposal_status_history`. Calls quote-service
(quote must be ACCEPTED, not expired) and customer-service (KYC VERIFIED). Domain events via
`@TransactionalEventListener`.

## Step 13 - `payment-service`

Feign + Resilience4j, plus `awaitility` and `spring-kafka-test` (test). Owns `payments` + `payment_outbox`.
`provider/PaymentProvider` port with `MockPaymentProvider`; `event/OutboxWriter`, `OutboxDispatcher`, `OutboxTransport`
(`HttpOutboxTransport` | `KafkaOutboxTransport`), `scheduler/OutboxRetryScheduler`; `PaymentTransactions` holds the short
transaction that commits the payment and the outbox row together. Calls proposal-service (amount, APPROVED) and
policy-service (issue, over HTTP or Kafka).

## Step 14 - `policy-service`

Feign + Resilience4j, `awaitility`, `spring-kafka-test`. Owns `policies`, `policy_renewal_reminders`.
`service/PolicyIssuanceService` (idempotent by payment reference, re-verifies the payment), `PolicyNumberGenerator`,
`PolicyScheduleGenerator` + `PolicyDocumentService` (async call to document-service), `RenewalPolicy`,
`scheduler/PolicyLifecycleScheduler`, `messaging/PaymentSucceededListener` (Kafka consumer). Calls payment, proposal,
document services.

## Step 15 - `claims-service`, Step 16 - `document-service`, Step 17 - `notification-service`

- **claims-service** (Feign + Resilience4j): `claims`, `claim_status_history`, `claim_documents`; calls policy-service
  `coverage-check` and document-service; introduces role `CLAIMS_HANDLER`; `PendingClaimReminderScheduler`.
- **document-service** (no Feign): `documents` metadata + files on disk; multipart upload, `POST /generated` for
  system files (SERVICE only), download, verify, soft delete.
- **notification-service** (Feign + Resilience4j + `spring-boot-starter-mail`, `spring-kafka-test`): `notifications`;
  `provider/ChannelProvider` with `MockEmailProvider`, `SmtpEmailProvider`, `MockSmsProvider`; `template/NotificationTemplates`;
  `client/RecipientResolver` (asks customer-service when the event carries no contact); `NotificationRetryScheduler`;
  `messaging/NotificationRequestedListener` (Kafka consumer).

Each of these was created the same way: copy the auth-service skeleton (POM, application.yml, test yml, Dockerfile),
rename the package, write `V1__create_<x>_tables.sql`, add its `<service>.yml` to the config-server and its route to
`api-gateway.yml`, then entities -> repositories -> services -> controllers -> tests.

---

## Step 18 - Dependency flow between the modules

**Compile time (Maven `<dependency>`):**

```
spring-boot-starter-parent 3.5.16
        |
insurance-ecommerce (parent POM, BOMs, plugin config)
   |            |             |              |
eureka-server  config-server  api-gateway   common-lib  <---- the ONLY internal dependency
                                                |
          auth, customer, product, quote, proposal, payment, policy, claims, document, notification
```
No business service depends on another business service's jar. Contracts between them are DTO records duplicated on
each side (for example `PaymentSuccessfulEvent` in payment-service and `IssuePolicyRequest` in policy-service): if a
field changes, both sides change explicitly, and no service can accidentally import another's entity.

**Runtime (network):** Step 20.

---

## Step 19 - Where it starts, how Maven builds it, and the build order

**Where the application starts.** There is no single "main". Each module has its own
`com.insurance.<x>.<X>Application` class with `public static void main(String[] args) { SpringApplication.run(...); }`
and is a separate JVM: `java -jar <module>/target/<module>-1.0.0-SNAPSHOT.jar`. Thirteen processes make one platform;
`scripts/run-local.sh` starts them all. Startup order at runtime: Eureka -> Config Server -> Gateway -> services in any
order (config-first bootstrap: each service reads `CONFIG_SERVER_URL`, pulls its YAML, runs Flyway, then registers).

**How Maven builds all modules.** `./mvnw install` at the root starts the **reactor**: Maven reads the parent's
`<modules>`, reads each child POM, builds a dependency graph between the modules, sorts it topologically, and then runs
the full lifecycle (`compile -> test -> package -> install`) module by module. `install` copies each jar into
`~/.m2/repository/com/insurance/...`, which is how `auth-service` resolves `com.insurance:common-lib:1.0.0-SNAPSHOT`
when you later build it alone with `./mvnw -pl auth-service package`.

**Build order and why.** The reactor output is:

```
1. insurance-ecommerce (parent, nothing to compile)
2. eureka-server   }
3. config-server   }  no internal dependencies -> keep the declared order
4. api-gateway     }
5. common-lib         <- must precede every service because they declare it as a dependency
6. auth-service, 7. customer-service, ... 14. notification-service   (declared order; independent of each other)
```
If `common-lib` had been listed last in `<modules>`, Maven would still build it fifth: the graph wins over the list.
A change in `common-lib` therefore requires `./mvnw -pl common-lib install` before rebuilding a single service, otherwise
that service compiles against the *old* jar in `~/.m2`.

---

## Step 20 - How the modules communicate at runtime

| Path | Mechanism | Details |
|---|---|---|
| client -> service | HTTP through the gateway | `Authorization: Bearer <JWT>`; the gateway validates it and forwards it plus `X-User-Id/Email/Roles`, `X-Correlation-Id`; the route table maps `/api/quotes/**` to `lb://quote-service` |
| gateway -> Eureka, service -> Eureka | Eureka REST | register at boot, heartbeat every 10 s, fetch the registry every 10 s |
| service -> Config Server | HTTP at boot | `GET /<name>/default` with Basic auth |
| service -> service | **OpenFeign** (declarative HTTP client) resolved by Eureka, never via the gateway | `@FeignClient(name = "customer-service")`; common-lib's interceptor adds a self-issued `SERVICE` JWT and the correlation id; `*Gateway` wrappers add Resilience4j circuit breaker + retry -> `503 SERVICE_UNAVAILABLE` naming the dependency |
| payment -> policy, everyone -> notification | Feign by default, **Kafka** with `KAFKA_ENABLED=true` | topics `payment.succeeded`, `notification.requested`, JSON values, per-service consumer groups, `.DLT` topics |
| service -> database | JDBC/Hikari + JPA | all services on `jdbc:mysql://localhost:3306/retail_db` locally, each with its own tables and its own Flyway history table |
| product, quote -> Redis | Spring Cache | optional; misses fall through to MySQL |

Trust model: the gateway and every service verify the JWT independently (same `JWT_SECRET`), so a service is safe even
if reached without the gateway; endpoints meant for other services are `@PreAuthorize("hasRole('SERVICE')")`.

---

## Step 21 - One complete request: `POST /api/auth/register`

1. **Client** sends `POST http://localhost:8080/api/auth/register` with JSON `{email, password, firstName, lastName, phone}`.
2. **api-gateway**: `CorrelationIdFilter` creates `X-Correlation-Id`; `JwtAuthenticationFilter` asks `PublicPathMatcher` -
   `/api/auth/register` is public, no token needed; route `auth-service` matches `Path=/api/auth/**`; Eureka resolves
   `lb://auth-service` to `localhost:8081`; the request is forwarded.
3. **auth-service, web layer**: common-lib's `CorrelationIdFilter` puts the id in the MDC; the `SecurityFilterChain`
   permits the path (`security.public-paths`); Spring MVC routes to `AuthController.register(@Valid @RequestBody RegisterRequest)`.
   Bean Validation runs first - an invalid e-mail becomes a 400 with `fieldErrors` from `GlobalExceptionHandler`.
4. **Service layer**: `AuthService.register()` is `@Transactional`:
   - `userRepository.existsByEmailIgnoreCase()` -> `DuplicateResourceException` (409) if taken;
   - `passwordEncoder.encode()` (BCrypt), `userRepository.save(User...)` with role `CUSTOMER` - Hibernate inserts into
     `users` and `user_roles`; `AuditableEntity` fills `created_at/by` via `platformAuditorAware`;
   - `customerProfileClient.createProfile(...)` -> Feign `POST /api/customers` on customer-service with a `SERVICE`
     token (customer-service's `CustomerController.create` is `@PreAuthorize("hasRole('SERVICE')")`) - a transport
     failure becomes `ServiceUnavailableException` (503);
   - `notifications.publish(REGISTRATION)` - parked until the transaction commits, then sent asynchronously;
   - `issueTokens(user)`: `JwtTokenIssuer.issueAccessToken()` (HS256, 15 min, claims `sub, email, roles`) and
     `RefreshTokenService.issue()` (random token, hash stored in `refresh_tokens`, 7 days).
5. **Repository/database**: `UserRepository extends JpaRepository<User, Long>` - Spring Data generates the SQL for
   `findByEmailIgnoreCase` / `existsByEmailIgnoreCase`; the transaction commits on return.
6. **Response**: `AuthResponse{accessToken, refreshToken, tokenType, expiresInSeconds, user}` with `201 Created`
   (`ResponseEntity.status(HttpStatus.CREATED)`), back through the gateway with the same `X-Correlation-Id`.

The same shape repeats everywhere: controller (HTTP + validation + roles) -> service (rules + transaction + clients)
-> repository (SQL) -> database, with common-lib supplying the security, error and audit plumbing around it.

---

## Step 22 - Why modules instead of one Spring Boot project

| Concern | One project | This layout |
|---|---|---|
| Deploy a payment fix | redeploy everything | rebuild and restart `payment-service` only |
| Scale quoting under load | scale the whole monolith | run three `quote-service` instances; Eureka + `lb://` spread the calls |
| Data ownership | one schema, any code can touch any table | each service is the only writer of its tables; no foreign keys cross a service (ready to split into one DB per service) |
| Failure isolation | one OOM kills quoting, payments and claims | a dead document-service only degrades policy issuance (async, logged); circuit breakers return 503 instead of hanging |
| Learning goals | none of Eureka, Config Server, Gateway, Feign, Resilience4j, outbox, Kafka would be needed | every one of them is exercised by a real call path |
| Cost | simplest possible | 13 JVMs, a registry, a config server, a gateway - and the whole point of the exercise |

The compromise that keeps it manageable: one repository, one parent POM, one shared MySQL schema locally, and one
`common-lib` so the cross-cutting code exists exactly once.
