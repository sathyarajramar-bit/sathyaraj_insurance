# auth-service design (Phase 2)

## 1. Responsibility
Identity only: who can log in, with which roles, and the tokens that prove it. It registers accounts,
checks passwords, signs access tokens, rotates refresh tokens and lets an ADMIN manage roles. It is the
only module in the platform that signs a JWT. Everything else about a person (profile, address, KYC,
vehicles) belongs to customer-service; auth-service keeps name and phone only to seed that profile.

## 2. Architecture
```text
client -> gateway -> auth-service ──Feign+SERVICE JWT──> customer-service  POST /api/customers (profile, idempotent per userId)
                        │
                        ├── retail_db (users, user_roles, refresh_tokens; flyway_history_auth_service)
                        └── NotificationPublisher -> REGISTRATION (after commit, async)
```
No cache, no scheduler. The gateway and every service verify the tokens issued here with the same
`security.jwt.secret` (HS256); nothing calls back to auth-service per request (stateless auth).
`AdminUserInitializer` runs at start-up and seeds the first ADMIN when `ADMIN_PASSWORD` is set.

## 3-4. Database (retail_db)
```text
users            id, email (uk, lower-cased), password_hash (BCrypt), first_name, last_name, phone, status ACTIVE|LOCKED,
                 audit (created_at/by, updated_at/by) + version      idx (status)
user_roles       user_id (fk, cascade) + role (pk together)           @ElementCollection, EAGER: roles ride along with every token
refresh_tokens   id, user_id (fk, cascade), token_hash (uk, SHA-256 hex), expires_at, revoked, audit + version
                 idx (user_id)                                        the raw token is never stored
```
A user has few roles that are always needed, so roles are an element collection, not an entity.

## 5. API
```text
POST /api/auth/register  {email, password, firstName, lastName, phone}   public   201 + tokens; 409 email taken; 503 customer-service down
POST /api/auth/login     {email, password}                                public   200 + tokens; 401 wrong credentials or LOCKED
POST /api/auth/refresh   {refreshToken}                                   public   new pair, old refresh token revoked; 401 invalid/expired/revoked
POST /api/auth/logout    {refreshToken}                                   bearer   204, revokes that refresh token
GET  /api/auth/me                                                         bearer   the caller's account
GET  /api/auth/users?email=&page=&size=&sort=createdAt,desc               ADMIN    paged search by e-mail fragment
GET  /api/auth/users/{id}                                                 ADMIN
PUT  /api/auth/users/{id}/roles {roles:[...]}                             ADMIN    replaces the role set (promote to AGENT, CLAIMS_HANDLER, ADMIN)
```
Token response: `accessToken`, `refreshToken`, `tokenType "Bearer"`, `expiresInSeconds`, `user`.
Public paths are declared both in `gateway.security.public-paths` and in this service's `security.public-paths`.

## 6. Token lifecycle
```text
access   JWT HS256; iss insurance-platform, sub = user id, email, roles [...], iat, exp = +15 min (security.jwt.access-token-ttl)
refresh  32 random bytes, base64url; only SHA-256(token) stored; 7 days (security.jwt.refresh-token-ttl)
rotate   refresh -> presented token revoked, new pair issued. Presenting an ALREADY revoked token = theft signal:
         every refresh token of that user is revoked and the call fails 401.
service  JwtTokenIssuer.issueServiceToken / common-lib ServiceTokenProvider: sub 0, roles [SERVICE], 5 min
```
A JWT cannot be revoked before `exp`; the short access lifetime plus revocable refresh tokens is the mitigation.

## 7. Business rules
- E-mail is unique (case-insensitive, stored lower-case). Password 8-72 chars with at least one letter and one digit;
  phone 10-15 digits with optional `+`. Validation errors are the platform 400 body.
- New accounts get role CUSTOMER and status ACTIVE. A LOCKED account cannot log in or refresh.
- `register` inserts the user, then calls customer-service inside the same transaction: if the profile call fails the
  insert rolls back, so nobody ends up with a login and no profile. The reverse (profile created, our commit fails) is
  harmless because customer-service's create is idempotent per `userId`. No distributed transaction, one owner per step.
- Transport failures to customer-service -> 503 naming the dependency; a 4xx from it -> 422 CUSTOMER_PROFILE_REJECTED.
- Role assignment replaces the whole set; `@Version` guards concurrent edits (409 CONCURRENT_MODIFICATION).
- Admin bootstrap is idempotent and skipped when `ADMIN_PASSWORD` is empty; Flyway cannot seed it because the BCrypt
  hash would have to be committed.

## 8. Communication
Sync: one Feign call (customer-service create) through Eureka, wrapped by `CustomerProfileClient` so Feign types never
leak into the service. Async: `NotificationPublisher` REGISTRATION (e-mail with the first name) after commit.
Inbound: none; other services never call auth-service, they verify tokens locally.

## 9. Security
`/register`, `/login`, `/refresh` are public; `/logout` and `/me` need any bearer token; `/users/**` is
`@PreAuthorize("hasRole('ADMIN')")` at class level. Passwords are BCrypt (common-lib `PasswordEncoder`). The
development secret is logged as a warning at start-up; set `JWT_SECRET` (>= 32 chars) anywhere shared.

## 10. Package structure
controller (AuthController, UserAdminController), service (AuthService, RefreshTokenService, UserService),
security (JwtTokenIssuer, InvalidCredentialsException -> 401), client (CustomerClient Feign contract, CustomerProfileClient
anti-corruption layer, DTOs), config (AdminBootstrapProperties, AdminUserInitializer), entity (User, Role, UserStatus,
RefreshToken), repository, dto, mapper.
