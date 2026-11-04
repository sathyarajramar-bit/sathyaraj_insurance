# customer-service design (Phase 2)

## 1. Responsibility
The customer master: profile (name, phone, date of birth, gender, address), the KYC workflow and the
insurable vehicles. It is created by auth-service at sign-up and read by quote-service (vehicle, IDV),
proposal-service (prefill, KYC status) and notification-service (e-mail, phone). It never prices, quotes
or stores documents; a KYC submission records the document type and number only.

## 2. Architecture
```text
auth-service ──Feign+SERVICE JWT──> POST /api/customers        (profile at registration, idempotent per userId)
client -> gateway -> customer-service
quote / proposal / notification ──Feign+SERVICE JWT──> GET /api/customers/{id} | /by-user/{userId} | /{id}/vehicles/{vehicleId}
                        └── retail_db (customers, vehicles; flyway_history_customer_service)
```
No outbound calls, no cache, no scheduler. Resilience is on the callers' side.

## 3-4. Database (retail_db)
```text
customers   id, user_id (uk, logical ref to users.id), email (uk), first_name, last_name, phone, date_of_birth, gender,
            kyc_status PENDING|VERIFIED|REJECTED, kyc_document_type PASSPORT|DRIVING_LICENCE|NATIONAL_ID, kyc_document_number,
            kyc_verified_at, address_line1/2, city, state, postal_code, country (embedded Address), audit + version
            idx (last_name)  <- admin search      idx (kyc_status)  <- KYC work queue
vehicles    id, customer_id (fk), registration_number (uk, normalised upper-case, no spaces/dashes), vehicle_type CAR|BIKE,
            make, model, variant, fuel_type PETROL|DIESEL|CNG|ELECTRIC|HYBRID, manufacturing_year, engine_capacity_cc,
            chassis_number, registration_date, current_value (IDV, DECIMAL 12,2), audit + version      idx (customer_id)
```
`Vehicle -> Customer` is a unidirectional `@ManyToOne`: loading a customer never drags the vehicles along, they are
paged by query. No FK to `users`: services only share logical ids.

## 5. API
```text
POST  /api/customers                        SERVICE              create profile (auth-service); returns the existing one for a known userId
GET   /api/customers/me                     CUSTOMER             my profile
PUT   /api/customers/me                     CUSTOMER             name, phone, date of birth, gender, address
PUT   /api/customers/me/kyc                 CUSTOMER             submit document type + number; status -> PENDING (resets a previous decision)
GET   /api/customers?name=&email=&kycStatus=&page=&size=&sort=   ADMIN, AGENT   paged search (Specification)
GET   /api/customers/{id}                   owner, ADMIN, AGENT, SERVICE
GET   /api/customers/by-user/{userId}       ADMIN, AGENT, SERVICE   lookup by auth user id
PATCH /api/customers/{id}/kyc {decision}    ADMIN, AGENT         VERIFIED | REJECTED
POST  /api/customers/me/vehicles            CUSTOMER             201
POST  /api/customers/me/vehicles/import?dryRun=   CUSTOMER       multipart "file" (.xlsx/.xls/.csv); 200 with VehicleImportResult
GET   /api/customers/me/vehicles/import-template  CUSTOMER       empty .xlsx with the header row
GET   /api/customers/me/vehicles            CUSTOMER             paged
PUT   /api/customers/me/vehicles/{vehicleId}            CUSTOMER
DELETE /api/customers/me/vehicles/{vehicleId}           CUSTOMER   204 (hard delete)
GET   /api/customers/{customerId}/vehicles              owner, ADMIN, AGENT, SERVICE   paged
GET   /api/customers/{customerId}/vehicles/{vehicleId}  owner, ADMIN, AGENT, SERVICE
```

## 6. KYC lifecycle
```text
(profile created) PENDING, no document
customer submits  -> PENDING with document type + number (any earlier VERIFIED/REJECTED is reset, kyc_verified_at cleared)
ADMIN/AGENT       -> VERIFIED (kyc_verified_at = now) | REJECTED
```
A decision needs a submitted document (400 otherwise) and cannot be PENDING. proposal-service refuses to submit a
proposal unless the status is VERIFIED.

## 7. Business rules
- Create is idempotent per `userId`: auth-service may retry after a timeout without producing duplicates; a different
  user with the same e-mail is a 409.
- Customer must be at least `customer.minimum-age-years` (18) when a date of birth is given.
- Vehicles: registration number unique platform-wide, normalised (`"ka 01-ab 1234"` -> `KA01AB1234`); manufacturing year not
  in the future and not older than `customer.vehicle.max-age-years` (20); registration date not before the manufacturing
  year. `current_value` is the IDV quote-service prices from.
- A customer can only touch their own vehicles (`findByIdAndCustomerId`), so a foreign vehicle id is a 404, not a 403.
- Spreadsheet import (`VehicleImportService`, the platform's version of the retail-java fleet upload): columns are
  matched by header name (`registrationNumber`, `Registration Number`, `reg_no` all work), every row goes through the
  same Bean Validation and `validateBusinessRules` as the form, duplicates inside the file and against `vehicles` are
  reported, and the file is all-or-nothing: with one bad row nothing is saved and every problem comes back with its
  row and column (`status REJECTED`). `dryRun=true` validates only (`VALID`). File-level problems (type, missing
  required column, more than `customer.vehicle.import-max-rows` = 200 rows) are the normal 400. Apache POI reads
  .xlsx/.xls; .csv is parsed in-house (RFC 4180 quoting). Real Excel dates and numbers are read as typed.

## 8. Communication
Inbound only. Callers use the SERVICE token from common-lib; the ownership check accepts SERVICE as a reader so a
service can fetch any customer on the platform's behalf. Thresholds live in `config/customer-service.yml`
so underwriting can change them without a build.

## 9. Security
Every controller method is role-gated with `@PreAuthorize`; the ownership rule is in one class,
`CustomerAccessPolicy`: owner, or ADMIN / AGENT / SERVICE. `/me/**` resolves the customer from the JWT user id, so
a customer can never address another profile by id.

## 10. Package structure
controller (CustomerController, VehicleController), service (CustomerService, VehicleService, VehicleImportService), security
(CustomerAccessPolicy), config (CustomerProperties), entity (Customer, Address @Embeddable, Vehicle, enums), repository
(+ CustomerSpecifications for the search filters), dto, mapper.
