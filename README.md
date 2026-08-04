# Octopus Bank

A full-stack banking application. Supports multi-currency accounts, real-time balance tracking, currency exchange, cross-user transfers, identity/KYC verification, and live balance-update push notifications.

---

## Architecture

Four Spring Boot services behind an API gateway, plus the Angular SPA:

```
                         ┌────────────────────┐
  Browser ──────────────▶│   gateway-service   │  :8080  (Spring Cloud Gateway)
  (Angular, proxies      │  routes by path      │
   /api → :8080)         └──────────┬───────────┘
                                     │
          ┌──────────────┬──────────┼──────────────────┬──────────────────┐
          │ /api/auth/**  │ /api/notifications/**        │ everything else
          │ /api/kyc/**   │                               │ (/api/accounts/**,
          ▼               ▼                               ▼  /api/exchange-rates)
┌────────────────────┐ ┌─────────────────────┐ ┌─────────────────────────┐
│  identity-service   │ │ notification-service │ │  core-banking-service    │  :8082
│  Users, Auth, JWT   │ │  :8083 (WebFlux)      │ │  no local Users table   │
│  issuance, 2FA/OTP, │ │  Live balance-update  │ │  Accounts, transfers,   │
│  KYC          :8081 │ │  SSE relay            │ │  exchange               │
└─────────┬───────────┘ └───────────┬───────────┘ └──────────┬───────────────┘
          │                          ▲                          │
     identitydb (H2/Postgres)        │ subscribes to        corebankingdb (H2/Postgres)
                                      │ "account-events"          │
                                      └────── Redis Pub/Sub ──────┘
```

- **identity-service** owns `User`, registration/login, JWT issuance, 2FA/OTP, and KYC. Only
  service with a Users table. `TokenBlacklist`, `OtpStore`, and `AccountRevocationStore` (a
  per-user revocation marker checked by all three services) are Redis-backed.
- **core-banking-service** owns `Account`/`Operation` and never looks a user up by ID — it trusts
  identity-service's JWT (shared secret, no DB round-trip, just a Redis revocation check) and
  denormalizes the owner's public UUID/username onto `Account` at creation. Transfers are gated on
  KYC and an external debit-eligibility check (circuit-breaker, fail-closed). Every balance change
  publishes an event for notification-service — see [Live balance updates](#live-balance-updates).
- **notification-service** is the one WebFlux/Reactor service in the stack — mostly idle,
  long-lived, I/O-bound SSE connections, the shape reactive I/O is for. Owns messages in MongoDB.
- **gateway-service** is the only service the frontend/browser ever talks to directly. Thin
  reverse proxy by path, plus Redis-backed per-IP rate limiting (stricter on `/api/auth/login`).
  Fails **open** (allows traffic) if Redis is unreachable, rather than taking the gateway down.

### Known simplifications (by design, for this project's scope)

- **Service-to-service calls** (`/internal/**`) are left unauthenticated — trust is the network
  boundary (per-service security groups, never routed through the gateway), not an application-layer
  credential. A larger deployment would likely use mTLS or a service mesh instead.
- **Logout only revokes the token you logged out with** (`TokenBlacklist`, per-token, checked by
  identity-service only). `AccountRevocationStore` (Redis, per-user) is already checked by all
  three services, but nothing calls it yet — there's no "log out everywhere" or admin
  account-disable action in the app, so it's a ready mechanism without a caller.
- **KYC verification is mocked** — there's no real document-authenticity/liveness vendor call.
  Submitting identity + document/selfie uploads only ever reaches `PENDING`; moving to
  `VERIFIED`/`REJECTED` is exclusively an admin console action (`/admin`, `Role.ADMIN` only).
- **DocumentDB runs with TLS disabled** (`docdb.tf`), to keep the reactive Mongo driver's
  connection string simple. A real deployment would leave TLS on.

### Live balance updates

The accounts screen updates itself the moment a transfer/exchange changes a balance, no refresh
needed. `AccountService.moveFunds` (core-banking) publishes to the Redis Pub/Sub channel
`account-events` after the surrounding transaction commits; `notification-service` subscribes once
and fans events out to every connected browser over SSE. Since the browser's `EventSource` can't
send an `Authorization` header, the frontend first exchanges its JWT for a short-lived, single-use
ticket (`POST /api/notifications/ticket`) and opens the stream with that instead of a bearer token.
New messages ride the same stream as a second, independently-typed SSE event
(`message-created` vs `balance-update`). See `notification.service.ts` and
`NotificationController` for the full wiring.

---

## Tech Stack

**Backend** — Java 21, Spring Boot 3.2, 4 services

| Concern        | Library                                                                |
| -------------- | ----------------------------------------------------------------------- |
| Gateway        | Spring Cloud Gateway 2023.0 (routing, Redis-backed rate limiting)        |
| Reactive       | Spring WebFlux + Project Reactor (notification-service only)            |
| Persistence    | Spring Data JPA, H2 (dev) / PostgreSQL (prod) — identity, core-banking.  |
|                | Spring Data MongoDB Reactive — notification-service                     |
| Shared state   | Redis — rate limiting, token blacklist/OTP/revocation marker,           |
|                | idempotency-key dedup, balance/message pub-sub, SSE tickets             |
| Security       | Spring Security, JWT (jjwt 0.12) — issued by identity-service            |
| Caching        | Spring Cache + Caffeine (5-min TTL on exchange rates)                    |
| Resilience     | Resilience4j circuit breakers (debit eligibility, KYC status)            |
| Object storage | AWS SDK v2 (S3) — MinIO locally, real S3 in prod, presigned KYC uploads  |
| Tests          | JUnit 5, Mockito, Spring Boot Test, WireMock, Reactor Test               |

**Frontend** — Angular 22 (standalone components, esbuild)

| Concern          | Library                                  |
| ---------------- | ----------------------------------------- |
| State management | NgRx Store 21 + Effects + Store Devtools  |
| Charts           | Chart.js 4 + ng2-charts 10                |
| PDF export       | jsPDF 4                                   |
| Tests            | Jest 29 + jest-preset-angular             |

**Infrastructure** — AWS (Terraform 1.10+)

S3 + CloudFront → Angular SPA. ECS Fargate → 5 services (identity, core-banking, gateway,
notification, debit-eligibility-mock) over ECS Service Connect. RDS PostgreSQL 16 → one instance
per service that owns data. ElastiCache Redis (AUTH-token + TLS required) → shared by every
service except debit-eligibility-mock. DocumentDB → notification-service's messages. S3 → KYC
uploads. ALB → routes only to gateway-service, restricted to CloudFront. Secrets Manager → DB
passwords, user-JWT secret, Redis AUTH token, mail creds.

---

## Running Locally

### Docker Compose (recommended)

```bash
docker compose up --build -d   # first run, or after a source change
docker compose up -d           # subsequent runs
```

Starts Redis, MinIO, Mongo, the WireMock debit-eligibility stub, all four Spring Boot services,
and the Angular dev server, in dependency order (health-checked, not just "container started").
Open **http://localhost:4200** once `frontend` reports healthy (`docker compose ps`). Source is
bind-mounted for hot-reload; `docker compose restart frontend` if a change doesn't show up.
`docker compose down` stops everything (`-v` to also drop the frontend's `node_modules` cache).

### Running natively

Six pieces, **in this order** (core-banking's/notification-service's seeders look demo users up
from identity-service over HTTP at startup, so it must be seeded first; everything needs Redis up
first too):

```bash
# Redis, MinIO, Mongo - credentials must match each service's dev-default application.properties
docker run -d --name bankapp-redis -p 6379:6379 redis:7-alpine \
  redis-server --requirepass d94064dfca2847fdde6b3bd5c81e7873
docker run -d --name bankapp-minio -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin \
  -e MINIO_API_CORS_ALLOW_ORIGIN=http://localhost:4200 \
  minio/minio server /data --console-address ":9001"
docker run -d --name bankapp-mongo -p 27017:27017 \
  -e MONGO_INITDB_ROOT_USERNAME=bankapp -e MONGO_INITDB_ROOT_PASSWORD=0ca39c46e075fcc9b4b5446eeab90e9f \
  mongo:7

cd identity-service && mvn spring-boot:run -Dspring-boot.run.profiles=dev   # :8081
cd core-banking && mvn spring-boot:run -Dspring-boot.run.profiles=dev      # :8082
cd gateway-service && mvn spring-boot:run                                   # :8080 - talk to the app here
cd notification-service && mvn spring-boot:run                              # :8083 - optional, just no live updates without it
cd frontend && npm install && npm start
```

`DataSeeder`/`MessageSeeder` register demo users `alice`/`bob`/`carol`/`bank` (password
`<username>123`, except `bank` which is login-disabled) and auto-verify their KYC on first run.
H2 consoles at `/h2-console` on :8081/:8082 (JDBC `jdbc:h2:mem:identitydb`/`corebankingdb`, user
`admin`/`admin123`). MinIO console at http://localhost:9001. Outgoing transfers also need the
debit-eligibility WireMock stub — see [Debit eligibility](#debit-eligibility).

---

## UI Tests (Playwright)

`frontend/e2e/` drives the real app end to end (real login/OTP, real transfers, real
currency-conversion math) rather than mocking the backend:

```bash
docker compose -f docker-compose.e2e.yml up --build -d   # redis, minio, mongo, wiremock, all 4 services
cd frontend && npm install
npx playwright install chromium   # first time only
npm run e2e                       # starts the e2e-configured dev server itself and runs the suite
docker compose -f docker-compose.e2e.yml down             # tear down when done
```

## Authentication

All endpoints except `/api/auth/**` require a `Bearer` token. identity-service is the only service
that issues tokens or checks passwords — registration and login both follow the same two-step
shape (`{challengeToken}` first, then `POST /api/auth/verify-otp` with an emailed/mocked code to
actually get a token). Every other service verifies the JWT's signature/expiry itself (shared
secret) plus a Redis check against `AccountRevocationStore`, with no per-request call back to
identity-service. Service-to-service calls (`/internal/**`) carry no credential at all — see
[Known simplifications](#known-simplifications-by-design-for-this-projects-scope).

---

## REST API Reference

All routes below are relative to the gateway (`http://localhost:8080`), the only address the
frontend or a browser should ever call directly.

### Auth (→ identity-service)

| Method | Path                   | Description                                                                  |
| ------ | ---------------------- | ---------------------------------------------------------------------------- |
| POST   | `/api/auth/register`   | Register `{username, email, password}` -> `{challengeToken}` (step 1 of 2FA) |
| POST   | `/api/auth/login`      | Login `{username, password}` -> `{challengeToken}` (step 1 of 2FA)           |
| POST   | `/api/auth/verify-otp` | Verify OTP `{challengeToken, code}` -> `{token, userId, username, admin}`    |
| POST   | `/api/auth/logout`     | Revoke the current token                                                     |

### KYC (→ identity-service)

| Method | Path                                       | Description                                                                               |
| ------ | ------------------------------------------ | ----------------------------------------------------------------------------------------- |
| GET    | `/api/kyc/status`                          | Current user's KYC status/level/identity details/document upload state                    |
| POST   | `/api/kyc/identity`                        | Submit identity `{firstName, lastName, issuingCountry, documentNumber}`                   |
| POST   | `/api/kyc/documents/upload-url`            | Request a presigned upload URL `{type: ID_DOCUMENT\|SELFIE}` -> `{documentId, uploadUrl}` |
| POST   | `/api/kyc/documents/{documentId}/complete` | Confirm the upload landed in storage; stays `PENDING` until an admin decides              |

Presigned-URL flow: the browser uploads directly to S3/MinIO (bytes never pass through our
services), then `complete` does a real `headObject` check before trusting it. Account creation
and outgoing transfers require `VERIFIED` status.

### Live balance updates (→ notification-service)

| Method | Path                        | Description                                                                               |
| ------ | --------------------------- | ----------------------------------------------------------------------------------------- |
| POST   | `/api/notifications/ticket` | Bearer-authenticated -> `{ticket}`, a single-use token valid for 15s                      |
| GET    | `/api/notifications/stream` | `?ticket=...` -> `text/event-stream` of this user's balance-update/message-created events |

See [Live balance updates](#live-balance-updates) for why this is ticket-based.

### Messages (→ notification-service)

The first two are bearer-authenticated and scoped to the caller's own messages. The third is
service-to-service only — never reachable by an end user's JWT, never routed through the gateway.

| Method | Path                                     | Description                                                                                                        |
| ------ | ---------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| GET    | `/api/notifications/messages`            | List the caller's messages, newest first                                                                           |
| POST   | `/api/notifications/messages/{id}/read`  | Mark one of the caller's messages as read (idempotent)                                                             |
| POST   | `/internal/messages`                     | Create a message `{ownerId, subject, body, priority?}` — persists + pushes a live event if `ownerId` is connected  |

### Accounts (→ core-banking-service)

All account endpoints operate on the authenticated user's own accounts. Cross-user access returns 403.

| Method | Path                                               | Description                                                                                                 |
| ------ | -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| POST   | `/api/accounts`                                    | Create account `{currency}` — requires KYC `VERIFIED`                                                       |
| GET    | `/api/accounts/user/{userId}`                      | List all accounts for a user                                                                                |
| GET    | `/api/accounts/{id}`                               | Account summary (id, accountNumber, currency, balance)                                                      |
| GET    | `/api/accounts/{id}/summary`                       | Account stats `{totalIn, totalOut}`                                                                          |
| GET    | `/api/accounts/{id}/balance-history`               | Balance over time (for chart)                                                                               |
| GET    | `/api/accounts/{id}/transactions?page=0&size=10`   | Paginated transaction history                                                                               |
| GET    | `/api/accounts/transactions/{txId}`                | Single transaction detail                                                                                   |
| POST   | `/api/accounts/{id}/exchange`                      | Exchange to another of your own accounts `{amount, targetAccountId}`                                        |
| POST   | `/api/accounts/{id}/transfer`                      | Send to another user `{amount, targetUsername, targetAccountNumber, description}` — requires KYC `VERIFIED` |
| GET    | `/api/accounts/recipient?username=&accountNumber=` | Pre-submit recipient check `{valid}`, no account details leaked                                             |

### Exchange Rates

Stored in the database (EUR as pivot currency), cached in memory 5 minutes (Caffeine).

| Method | Path                  | Description             |
| ------ | --------------------- | ------------------------ |
| GET    | `/api/exchange-rates` | All rate pairs (cached)  |

| Currency | Rate to EUR | Currency | Rate to EUR |
| -------- | ----------- | -------- | ----------- |
| EUR      | 1.00000000  | GBP      | 1.17000000  |
| USD      | 0.92000000  | SEK      | 0.08700000  |
| CHF      | 1.05000000  | PLN      | 0.23000000  |

### Debit eligibility

Outgoing transfers call an external eligibility service before moving funds (circuit-breaker
wrapped — down/timeout means fail-closed). No real vendor exists for this project, so both locally
and in AWS this points at a WireMock stub (`core-banking/wiremock/mappings/`) — `alice` allowed,
`bob` denied, `carol` returns a 500 (triggers fail-closed).

- **Locally**: `docker run -d --name bankapp-wiremock -p 8089:8080 -v "$(pwd)/core-banking/wiremock/mappings:/home/wiremock/mappings" wiremock/wiremock:3.4.2`
- **In AWS**: deployed by Terraform as its own ECS Fargate service — set the `debit_eligibility_url` variable once a real vendor exists, to swap it in with no code changes.

---

## Frontend Pages

| URL                 | Page                                                                                    |
| ------------------- | ----------------------------------------------------------------------------------------- |
| `/login`            | Login / Register                                                                        |
| `/`                 | Dashboard — all accounts, currency totals, open new account (hidden until KYC verified) |
| `/accounts/:id`     | Account overview — balance chart, paginated transactions, send/exchange modals          |
| `/transactions/:id` | Transaction detail — full breakdown with PDF export                                     |
| `/kyc`              | Identity verification — name/country/document number, then ID photo + selfie upload     |

NgRx manages state in three feature slices: **accounts** (list + live balance patches via SSE),
**account-detail** (current account, transactions, balance history, exchange rates), and
**transaction** (single transaction detail).

---

## Production Deployment (AWS)

Terraform provisions the same architecture as local: ALB/CloudFront only ever reach
**gateway-service**; everything else is reached internally over ECS Service Connect.

```bash
./infrastructure/scripts/bootstrap-state.sh    # one-time per create/destroy cycle: creates the S3 state bucket

cd infrastructure/terraform
cp terraform.tfvars.example terraform.tfvars   # fill in mail_*
terraform apply
```

Requires: AWS CLI configured, Terraform >= 1.10, Docker, Maven 3.9+/Java 21, and a real SMTP
relay (identity-service sends real OTP emails in prod). Provisions VPC/NAT/ALB, 5 ECS Fargate
services, 2 RDS Postgres instances (one per service that owns data), ElastiCache Redis
(AUTH+TLS), DocumentDB, S3 (frontend + KYC uploads), 5 ECR repos, and Secrets Manager entries for
every credential above.

```bash
# Deploy one service (build -> image -> ECR -> force ECS redeploy) - all read terraform output automatically
./infrastructure/scripts/deploy-service.sh <identity|core-banking|gateway|notification|debit-eligibility-mock>

./infrastructure/scripts/deploy-frontend.sh   # ng build -> S3 sync -> CloudFront invalidation

terraform output app_url   # the deployed CloudFront URL
```

Tearing down: `./infrastructure/scripts/teardown-state.sh` instead of raw `terraform destroy` if
you want to recreate later — it destroys the stack, then the state bucket, in the right order.

**Known gap**: ECS can't wait for a *dependent service's container* to be healthy before starting
another (only for the AWS resource to exist) — core-banking's `DataSeeder` may crash-and-restart
once or twice at boot if identity-service isn't ready yet, self-healing automatically.
