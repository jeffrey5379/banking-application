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
│  Users, Auth, JWT   │ │  :8083 (WebFlux)      │ │  (this repo's "backend")│
│  issuance, 2FA/OTP, │ │  Live balance-update  │ │  Accounts, transfers,   │
│  KYC          :8081 │ │  SSE relay            │ │  exchange               │
└─────────┬───────────┘ └───────────┬───────────┘ └──────────┬───────────────┘
          │                          ▲                          │
     identitydb (H2/Postgres)        │ subscribes to        corebankingdb (H2/Postgres)
                                      │ "account-events"          │
                                      └────── Redis Pub/Sub ──────┘
                                             (published after
                                              every commit)
```

- **identity-service** owns `User`, registration/login, JWT issuance, 2FA/OTP, and KYC (document
  submission + verification status). It's the only service with a Users table. `TokenBlacklist`
  (revoked JWTs) and `OtpStore` (2FA challenges) are Redis-backed, not local maps.
- **core-banking-service** (this repo's original `backend`) owns `Account`/`Operation` and never
  looks a user up by ID — it trusts the JWT identity-service issued (shared signing secret,
  verified statelessly, no DB round-trip) and denormalizes the owner's public UUID + username
  directly onto `Account` at creation time. Account creation and outgoing transfers call
  identity-service's internal KYC endpoint (circuit-breaker wrapped, fails closed) before
  proceeding. `IdempotencyStore` (Idempotency-Key deduplication) is Redis-backed too. Every
  balance change publishes a `BalanceEventPublisher` event for notification-service to relay -
  see [Live balance updates](#live-balance-updates).
- **notification-service** is the one WebFlux/Reactor service in the stack (everywhere else is
  the standard Spring MVC servlet model) - see [Live balance updates](#live-balance-updates) for
  why that fit here and how it's wired end to end.
- **gateway-service** is a thin Spring Cloud Gateway reverse proxy: `/api/auth/**` and
  `/api/kyc/**` go to identity-service, `/api/notifications/**` goes to notification-service,
  everything else under `/api/**` goes to core-banking. It's the only service the frontend (or a
  browser) ever talks to directly. It also rate-limits every request, **Redis-backed**
  (`RedisRateLimiter`), keyed by client IP + route id (see `RateLimiterConfig` — the key must
  include the route id, since `RedisRateLimiter`'s Redis key comes purely from the key resolver,
  with no route id mixed in on its own side; two routes resolving to the same key would silently
  share one token bucket). `/api/auth/login` gets a strict limit (burst 5, then a ~1/sec trickle)
  since it's the actual brute-force surface; everything else is looser (burst 20, ~5/sec
  trickle). The notifications route has no rate limiter at all - it's a single long-lived SSE
  connection, not a discrete request the token-bucket model fits. If Redis itself is unreachable,
  Spring Cloud Gateway fails **open** — requests are allowed through rather than the whole
  gateway going down — verified locally by stopping Redis and confirming traffic still flows.

### Known simplifications (by design, for this project's scope)

- **Internal service-to-service endpoints** (`/internal/kyc/**`, `/internal/users/**` on
  identity-service; `/internal/messages` on notification-service) are unauthenticated and never
  routed through the gateway. A real deployment would put mTLS or a service-to-service credential
  on this trust boundary.
- **Token revocation doesn't propagate**: logging out blacklists a token against identity-service
  only. core-banking validates JWTs statelessly and has no way to check that blacklist, so a
  revoked-but-unexpired token still works there until it naturally expires. Tokens are short-lived
  by design to bound this window.
- **KYC verification is mocked**, same spirit as the existing OTP mock and debit-eligibility
  WireMock stub: the identity form plus a real ID document photo + selfie upload (see the KYC
  section under "REST API Reference" below) auto-verifies to `BASIC` instead of calling a real
  document-authenticity/liveness vendor - there's no actual face-match or OCR happening.
- **KYC bucket/CORS provisioning happens at app startup** (`KycStorageInitializer`), in addition
  to Terraform already creating the bucket + its CORS policy declaratively (see
  `infrastructure/terraform/s3_kyc.tf`). The app-side call is redundant on real AWS S3 (it just
  re-converges to what Terraform already set) but is kept because it's also what makes local dev
  against MinIO self-sufficient without a Terraform run - see "Running Locally" for why it's
  wrapped in a try/catch there (MinIO Community Edition doesn't implement the S3 `PutBucketCors`
  API at all).
- **DocumentDB runs with TLS disabled** (`infrastructure/terraform/docdb.tf`'s cluster parameter
  group sets `tls = disabled`), purely so the reactive Mongo driver's connection string
  (`MONGO_URI`) doesn't need a CA bundle/trust store wired into the container. A real deployment
  would leave TLS on and have the driver trust the Amazon RDS CA bundle instead.

### Live balance updates

The accounts screen updates itself the moment a transfer or exchange changes a balance - no
manual refresh, no polling. End to end:

1. `AccountService.moveFunds` (core-banking) publishes a `BalanceEventPublisher` event - one per
   affected account - to the Redis Pub/Sub channel `account-events`. Publishing is deferred to
   **after the surrounding transaction commits** (`TransactionSynchronizationManager`), so a
   rolled-back transfer never fires a phantom notification. The payload is plain JSON, not the
   app's general-purpose `RedisTemplate<String, Object>` serialization (that one embeds a Java
   class name only core-banking itself could resolve) - a cross-service wire contract needs to
   stand on its own.
2. **notification-service** subscribes to that channel exactly once at startup and fans it out to
   every connected browser via an in-memory `Sinks.Many`, filtered per connection to that user's
   own `ownerId` - one Redis subscription serves any number of open tabs, rather than one per tab.
3. The browser holds that connection open via `EventSource` (Server-Sent Events) rather than a
   WebSocket - simpler here since the data only ever flows server → client. `EventSource` can't
   send an `Authorization` header, so it can't carry a JWT directly; instead the frontend first
   calls `POST /api/notifications/ticket` (a normal Bearer-authenticated request) to exchange its
   JWT for a short-lived, single-use ticket, then opens `GET /api/notifications/stream?ticket=...`
   with that ticket in the query string instead. A long-lived token sitting in a URL would end up
   in ALB/CloudFront access logs and browser history; a 15-second single-use ticket sitting there
   is a non-issue.
4. Reconnection is handled explicitly by `notification.service.ts` (fetch a fresh ticket, then
   reopen) rather than relying on `EventSource`'s built-in retry, which would just resend the
   same now-expired ticket forever.

New messages ride the exact same plumbing as a second, independent event type on the same
stream: `POST /internal/messages` (service-to-service only, see REST API Reference) persists the
message to MongoDB, then publishes a `MessageEventPublisher` event to its own Redis channel
(`message-events`) - same after-persist/best-effort/plain-JSON reasoning as
`BalanceEventPublisher` above, just without the transactional-commit hook, since a reactive Mongo
save has nothing equivalent to `TransactionSynchronizationManager` to defer onto. A second
`MessageEventSubscriber` fans it out the same way `AccountEventSubscriber` does, and
`NotificationController.stream()` merges both `Sinks.Many` Fluxes into one response, distinguishing
them for the client by SSE `event` name (`balance-update` vs `message-created`) rather than by
opening a second connection. `notification.service.ts` listens for both event names on the one
`EventSource` and reacts differently: `balance-update` dispatches an NgRx action and conditionally
toasts; `message-created` prepends to `MessagesService`'s in-memory list (so the unread badge
updates immediately) and always toasts.

This is also the one WebFlux/Reactor service in an otherwise all-servlet-MVC stack, and
deliberately so: it's almost entirely idle, long-lived, per-user connections (cheap on Netty's
event-loop model, expensive one-thread-per-connection) and naturally I/O-bound (Redis pub/sub in,
SSE out), exactly the shape reactive I/O is for. It also owns one piece of actual state - user
messages - stored in MongoDB via the reactive driver (`spring-boot-starter-data-mongodb-reactive`)
rather than R2DBC/Postgres, so nothing on this service's request path ever blocks the event loop.
The other two services stay on blocking JPA/Postgres since they're standard servlet MVC anyway;
this was the one place reactive-all-the-way-down was free.

---

## Tech Stack

**Backend** — Java 21, Spring Boot 3.2, 4 services (see Architecture above)

| Concern        | Library                                                           |
| -------------- | ----------------------------------------------------------------- |
| Gateway        | Spring Cloud Gateway 2023.0 (routing, Redis-backed rate limiting) |
| Reactive       | Spring WebFlux + Project Reactor (notification-service only —     |
|                | everywhere else is the standard servlet/Spring MVC model)         |
| Persistence    | Spring Data JPA, H2 (dev), PostgreSQL (prod) — identity-service &  |
|                | core-banking. Spring Data MongoDB Reactive — notification-service |
|                | (see Architecture above for why)                                   |
| Shared state   | Redis — rate limiting (gateway), token blacklist + OTP challenges |
|                | (identity-service), idempotency-key dedup (core-banking),         |
|                | balance-update pub/sub + SSE tickets (notification-service)       |
| Security       | Spring Security, JWT (jjwt 0.12) — issued by identity-service,    |
|                | verified statelessly everywhere else                              |
| Caching        | Spring Cache + Caffeine (5-min TTL on exchange rates)             |
| Resilience     | Resilience4j circuit breakers (debit eligibility, KYC status)     |
| Object storage | AWS SDK v2 (S3) — MinIO locally, real AWS S3 in prod, presigned   |
|                | uploads for KYC document/selfie photos (identity-service)         |
| Observability  | Spring Actuator (`/actuator/health`) on every service             |
| Utilities      | Lombok, Bean Validation                                           |
| Tests          | JUnit 5, Mockito, Spring Boot Test, WireMock, Reactor Test        |

**Frontend** — Angular 22 (standalone components, esbuild)

| Concern          | Library                                  |
| ---------------- | ---------------------------------------- |
| State management | NgRx Store 21 + Effects + Store Devtools |
| Charts           | Chart.js 4 + ng2-charts 10               |
| PDF export       | jsPDF 4                                  |
| Reactive         | RxJS 7.8                                 |
| Language         | TypeScript 6                             |
| Tests            | Jest 29 + jest-preset-angular            |

**Infrastructure** — AWS (Terraform 1.10+)

S3 + CloudFront -> Angular SPA
ECS Fargate -> 5 services (identity-service, core-banking-service, gateway-service,
notification-service, debit-eligibility-mock), wired together over ECS Service Connect
RDS PostgreSQL 16 -> one instance per service that owns data (identity, core-banking)
ElastiCache Redis -> shared by identity-service, core-banking-service, gateway-service,
notification-service
DocumentDB (MongoDB 5.0-compatible) -> notification-service's messages, single instance, TLS
disabled for driver-config simplicity (see docdb.tf and "Known simplifications"). Connection
string (with embedded credentials) generated by Terraform and stored as the MONGO_URI secret.
S3 -> KYC document/selfie uploads (identity-service), private + CORS-configured
ALB -> routes only to gateway-service (restricted to CloudFront IPs), 300s idle timeout
(long enough for notification-service's open SSE connections, AWS default is 60s)
ECR -> 5 Docker image registries, one per service
Secrets Manager -> DB passwords, JWT secret, mail credentials

---

## Running Locally

### One command (Docker Compose)

```bash
docker compose up --build -d   # first run, or after a backend dependency/source change
docker compose up -d           # subsequent runs
```

`docker-compose.yml` builds and starts everything below - Redis, MinIO, Mongo, the WireMock
debit-eligibility stub, all four Spring Boot services, and the Angular dev server - in the
dependency order described below, using Docker healthchecks (not just "container started") so
e.g. core-banking never starts before identity-service is actually ready to answer its
`DataSeeder` lookup. Open **http://localhost:4200** once `frontend` reports healthy
(`docker compose ps`). The frontend container bind-mounts `./frontend`, so editing source on the
host hot-reloads the running dev server; if a change doesn't show up, `docker compose restart
frontend` (Vite's file-watcher can occasionally miss an event on a Windows bind mount).

`docker compose down` stops everything (add `-v` to also drop the frontend's cached
`node_modules` volume). Every service still publishes its normal port to localhost
(8080-8083, 6379, 9000-9001, 8089, 27017), same as the manual steps below.

The rest of this section is the equivalent six manual steps, useful if you want to run one piece
natively (e.g. a backend service in your IDE's debugger) while Docker handles the rest, or don't
want Docker at all.

### Manual steps

Six things, started **in this order** (core-banking's `DataSeeder` and notification-service's
`MessageSeeder` both call identity-service over HTTP at startup to look up the demo users it
seeded, so identity-service must already be up and finished seeding first; all four backend
services - identity, core-banking, gateway, and notification - need Redis reachable before they
start; identity-service also needs MinIO reachable before it starts; notification-service also
needs Mongo reachable before it starts):

### 0. Redis + MinIO + Mongo

All four backend services share one Redis instance (rate limiting, token blacklist/OTP,
idempotency-key dedup, balance-update pub/sub + SSE tickets - see Architecture). Each fails in a
different way if it's not there: identity-service's login/logout/2FA endpoints will throw,
core-banking's Idempotency-Key handling will throw, notification-service's SSE stream simply never
receives anything, but the gateway's rate limiter specifically fails **open** (see Architecture).

identity-service also stores KYC document/selfie uploads in S3-compatible object storage - MinIO
locally, real AWS S3 in prod (see the KYC section under "REST API Reference" below). Unlike
Redis, bucket creation is a hard startup dependency: `KycStorageInitializer` creates the bucket on
boot and fails fast (identity-service won't start) if it can't reach MinIO, the same fail-fast
choice as an unreachable database.

notification-service stores user messages in MongoDB (see Architecture) - reachability is a hard
startup dependency the same way a relational database would be for a JPA service.

```bash
docker run -d --name bankapp-redis -p 6379:6379 redis:7-alpine
docker run -d --name bankapp-minio -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin \
  -e MINIO_API_CORS_ALLOW_ORIGIN=http://localhost:4200 \
  minio/minio server /data --console-address ":9001"
docker run -d --name bankapp-mongo -p 27017:27017 mongo:7
```

MinIO's web console is at http://localhost:9001 (login `minioadmin` / `minioadmin`) if you want to
browse uploaded files.

**CORS is configured via that `MINIO_API_CORS_ALLOW_ORIGIN` environment variable, not by the app.**
`KycStorageInitializer` does also call the S3 `PutBucketCors` API on boot (real AWS S3 implements
it, and a real deployment would still want it self-configured), but MinIO Community Edition
doesn't implement that API at all - it always answers `501 Not Implemented` for it regardless of
request shape or SDK version (verified against AWS SDK versions from 2.20.0 through 2.31.78, same
error every time), because MinIO expects CORS to be set server-wide via this env var instead. That
call is wrapped in a try/catch that logs a warning rather than failing startup, specifically
because of this known MinIO gap - if you forget the env var above, identity-service still starts,
but browser uploads to MinIO will fail their CORS preflight until you add it and restart the
container.

### 1. identity-service

```bash
cd identity-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Starts at **http://localhost:8081**. Uses its own H2 in-memory database (`identitydb`). On first
run, `DataSeeder` registers the demo users (`alice`/`bob`/`carol`/`bank`, all password
`<username>123` except `bank`) and auto-verifies their KYC - seeding marks their documents as
uploaded directly in the database, bypassing MinIO entirely, so MinIO only needs to be _reachable_
for the bucket/CORS bootstrap, not actually populated with seed files. `bank` is disabled for
login (see Architecture) but still a real, fully modelled user under the hood.

- H2 Console at http://localhost:8081/h2-console (JDBC URL: `jdbc:h2:mem:identitydb`, user: `admin`, password: `admin123`)

### 2. core-banking-service (this repo's `backend`)

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Starts at **http://localhost:8082**. Uses its own H2 in-memory database (`corebankingdb`). On
first run, `DataSeeder` creates the fake "bank" reserve account and every demo user's starting
accounts/balances via real double-entry transfers (see Architecture) — no money is credited out
of thin air anywhere in this service.

- H2 Console at http://localhost:8082/h2-console (JDBC URL: `jdbc:h2:mem:corebankingdb`, user: `admin`, password: `admin123`)

Outgoing transfers also call an external debit-eligibility check — see
[Debit eligibility](#debit-eligibility) for how to run its WireMock stub.

### 3. gateway-service

```bash
cd gateway-service
mvn spring-boot:run
```

Starts at **http://localhost:8080** — this is the address the frontend talks to.

### 4. notification-service

```bash
cd notification-service
mvn spring-boot:run
```

Starts at **http://localhost:8083**. Needs Mongo reachable (message storage) and identity-service
already up and seeded (`MessageSeeder` looks up alice/bob/carol's ids the same way core-banking's
`DataSeeder` does), plus Redis for the SSE side (see [Live balance
updates](#live-balance-updates)). If you skip this one, the rest of the app still works fully; the
accounts screen just won't update itself without a manual refresh, and the Messages page will be
empty.

### 5. Frontend

```bash
cd frontend
npm install
npm start
```

---

## UI Tests (Playwright)

`frontend/e2e/` drives the real app end to end with [Playwright](https://playwright.dev) - real
login/OTP, real transfers between the seeded demo users, real currency-conversion math - rather
than mocking the backend. That means the whole stack from "Running Locally" above must actually be
running first, which `docker-compose.e2e.yml` (repo root) does in one shot instead of six manual
steps:

```bash
docker compose -f docker-compose.e2e.yml up --build -d   # redis, minio, mongo, wiremock, all 4 services
cd frontend
npm install
npx playwright install chromium   # first time only
npm run e2e                       # starts `ng serve --configuration e2e` itself and runs the suite
docker compose -f docker-compose.e2e.yml down             # tear down when done
```

A few things that shape how these tests are written, all covered in comments in
`frontend/e2e/support/helpers.ts`:

- **The app's Router runs on a `NoopLocationStrategy`**
  (`frontend/src/app/routing/no-op-location-strategy.ts`): the address bar is never updated by
  in-app navigation, and a fresh page load always starts at the empty route regardless of the URL
  requested. So there's no deep-linking and nothing to `expect(page).toHaveURL(...)` - every test
  navigates by clicking through the UI (like a real user) and asserts on rendered content instead.
- **Seeded balances aren't the round numbers `DataSeeder` passes in.** Every demo account is
  funded from the bank's EUR reserve, and `AccountService` converts that EUR amount into the
  target account's own currency at the seeded exchange rate - so carol's "7000.00 EUR" seed is a
  real balance of 7,608.70 USD, not 7,000.00. `02-accounts.spec.ts` asserts the converted values.
- **Tests run with a single worker, in filename order** (`01-`, `02-`, `03-...`), because they
  mutate shared, real backend state (an actual transfer moves real money) rather than resetting
  fixtures between tests - see the ordering comment in `02-accounts.spec.ts`.
- **The gateway's rate limiter is real too.** A UI test suite fires far more requests per second
  than a human clicking around, so `docker-compose.e2e.yml` raises `RATE_GENERAL_REPLENISH` /
  `RATE_GENERAL_BURST` well above the production defaults in `gateway-service/application.yml`
  (still IP-keyed, still real) instead of the suite intermittently tripping its own brute-force
  protection.
- Re-running the suite against the **same** running stack a second time will fail the balance
  assertions in `02-accounts.spec.ts` and `03-transfer.spec.ts`, since the first run's transfer
  already moved real money - restart the stack (`down` then `up -d`) for a clean run.

## Authentication

All endpoints except `/api/auth/**` require a `Bearer` token in the `Authorization` header.
identity-service is the only service that ever issues a token (via OTP verification, at the end of
either the register or login flow - see below) or checks a password; every other service
(core-banking) verifies the token's signature and expiry itself (shared secret) and reads the
caller's identity straight from its claims — no per-request call back to identity-service for
routine authentication.

Registration and login both follow the same two-step shape: the first call never returns a token
by itself, only a `challengeToken` for a one-time code emailed (or in dev, mocked/logged) to the
user. `POST /api/auth/verify-otp` with that code is what actually establishes a session - this
applies to registration too, not just login, so a typo'd/unowned email can't be used to open an
account.

---

## REST API Reference

All routes below are relative to the gateway (`http://localhost:8080`), which is the only address
the frontend or a browser should ever call directly.

### Auth (→ identity-service)

| Method | Path                   | Description                                                                  |
| ------ | ---------------------- | ---------------------------------------------------------------------------- |
| POST   | `/api/auth/register`   | Register `{username, email, password}` -> `{challengeToken}` (step 1 of 2FA) |
| POST   | `/api/auth/login`      | Login `{username, password}` -> `{challengeToken}` (step 1 of 2FA)           |
| POST   | `/api/auth/verify-otp` | Verify OTP `{challengeToken, code}` -> `{token, userId, username}`           |
| POST   | `/api/auth/logout`     | Revoke the current token                                                     |

### KYC (→ identity-service)

| Method | Path                                       | Description                                                                               |
| ------ | ------------------------------------------ | ----------------------------------------------------------------------------------------- |
| GET    | `/api/kyc/status`                          | Current user's KYC status/level/identity details/document upload state                    |
| POST   | `/api/kyc/identity`                        | Submit identity `{firstName, lastName, issuingCountry, documentNumber}`                   |
| POST   | `/api/kyc/documents/upload-url`            | Request a presigned upload URL `{type: ID_DOCUMENT\|SELFIE}` -> `{documentId, uploadUrl}` |
| POST   | `/api/kyc/documents/{documentId}/complete` | Confirm the upload landed in storage; auto-verifies once both are in                      |

The document/selfie flow is presigned-URL-based: the browser gets a short-lived S3/MinIO PUT URL
from identity-service, uploads the file **directly** to storage (the bytes never pass through any
of our services), then calls `complete` - which does a real `headObject` check against storage
before trusting the upload, rather than taking the client's word for it. Verification itself is
still mocked (see Architecture's "Known simplifications"): once the identity form is submitted and
both an ID document photo and a selfie are confirmed uploaded, that auto-verifies to `BASIC`
instead of calling a real document/liveness vendor. Account creation and outgoing transfers on
core-banking both require `VERIFIED` status; the dashboard hides the "New Account" control until
then.

### Live balance updates (→ notification-service)

| Method | Path                        | Description                                                                       |
| ------ | --------------------------- | ---------------------------------------------------------------------------------- |
| POST   | `/api/notifications/ticket` | Bearer-authenticated -> `{ticket}`, a single-use token valid for 15s               |
| GET    | `/api/notifications/stream` | `?ticket=...` -> `text/event-stream` of this user's balance-update/message-created events |

See [Live balance updates](#live-balance-updates) for why this is ticket-based rather than a
normal `Authorization` header like every other endpoint here, and for how the two SSE event types
share this one stream.

### Messages (→ notification-service)

The first two are bearer-authenticated like every notification-service endpoint except `/stream`
above, and scoped to the authenticated user's own messages, stored in MongoDB (see Architecture).
The third is service-to-service only (see "Known simplifications") - creating a message for an
arbitrary `ownerId` must never be reachable by an end user's own JWT, so it's deliberately a
separate, unauthenticated `/internal/**` path rather than a third verb on the public
`/api/notifications/messages` resource, and (unlike `/api/**`) is never routed through the
gateway.

| Method | Path                                     | Description                                                                    |
| ------ | ----------------------------------------- | ------------------------------------------------------------------------------- |
| GET    | `/api/notifications/messages`             | List the caller's messages, newest first                                        |
| POST   | `/api/notifications/messages/{id}/read`   | Mark one of the caller's messages as read (idempotent)                          |
| POST   | `/internal/messages`                      | Create a message `{ownerId, subject, body, priority?}` (`priority` defaults to `NORMAL`) - persists it and pushes a `message-created` SSE event + frontend toast to `ownerId` if they're currently connected |

`MessageSeeder` uses none of these - it writes demo data directly via `MessageRepository` at
startup, which is why the three seeded messages per demo user don't arrive as toasts.

### Accounts (→ core-banking-service)

All account endpoints operate on the authenticated user's own accounts. Cross-user access returns 403.

| Method | Path                                               | Description                                                                                                 |
| ------ | -------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| POST   | `/api/accounts`                                    | Create account `{currency}` — requires KYC `VERIFIED`                                                       |
| GET    | `/api/accounts/user/{userId}`                      | List all accounts for a user                                                                                |
| GET    | `/api/accounts/{id}`                               | Account summary (id, accountNumber, currency, balance)                                                      |
| GET    | `/api/accounts/{id}/summary`                       | Account stats `{totalIn, totalOut}`                                                                         |
| GET    | `/api/accounts/{id}/balance-history`               | Balance over time (for chart)                                                                               |
| GET    | `/api/accounts/{id}/transactions?page=0&size=10`   | Paginated transaction history                                                                               |
| GET    | `/api/accounts/transactions/{txId}`                | Single transaction detail                                                                                   |
| POST   | `/api/accounts/{id}/exchange`                      | Exchange to another of your own accounts `{amount, targetAccountId}`                                        |
| POST   | `/api/accounts/{id}/transfer`                      | Send to another user `{amount, targetUsername, targetAccountNumber, description}` — requires KYC `VERIFIED` |
| GET    | `/api/accounts/recipient?username=&accountNumber=` | Pre-submit recipient check `{valid}`, no account details leaked                                             |

### Exchange Rates

Rates are stored in the database (EUR as pivot currency) and cached in memory for **5 minutes** (Caffeine).

| Method | Path                  | Description             |
| ------ | --------------------- | ----------------------- |
| GET    | `/api/exchange-rates` | All rate pairs (cached) |

Supported currencies and seeded rates:

| Currency | Rate to EUR |
| -------- | ----------- |
| EUR      | 1.00000000  |
| USD      | 0.92000000  |
| CHF      | 1.05000000  |
| GBP      | 1.17000000  |
| SEK      | 0.08700000  |
| PLN      | 0.23000000  |

### Debit eligibility

Outgoing transfers call an external eligibility service (`${debit.eligibility.url}/debit-eligibility/{username}`) before moving funds — keyed by username now that Users live in a separate service/database from Accounts. A Resilience4j circuit breaker wraps the call — if the service is down or times out, the transfer is rejected (fail-closed).

There's no real vendor for this in an educational project, so both locally and in AWS this points at the same WireMock stub (`backend/wiremock/mappings/`) — `alice` allowed, `bob` denied, `carol` returns a 500 (triggers fail-closed via the circuit breaker). Without it reachable, every transfer attempt fails closed the same way.

- **Locally**: run it on port 8089, e.g. `wiremock --port 8089 --root-dir backend/wiremock`, or via Docker: `docker run -d --name bankapp-wiremock -p 8089:8080 -v "$(pwd)/backend/wiremock/mappings:/home/wiremock/mappings" wiremock/wiremock:3.4.2`.
- **In AWS**: `terraform apply` deploys it as its own small ECS Fargate service (`backend/wiremock/Dockerfile` packages the same mappings into the `wiremock/wiremock` image), reached internally by core-banking over ECS Service Connect - see `infrastructure/terraform/ecs.tf`'s `debit_eligibility_mock` resources. Set the `debit_eligibility_url` Terraform variable once a real vendor exists, to bypass the mock without any code changes.

---

## Frontend Pages

| URL                 | Page                                                                                    |
| ------------------- | --------------------------------------------------------------------------------------- |
| `/login`            | Login / Register                                                                        |
| `/`                 | Dashboard — all accounts, currency totals, open new account (hidden until KYC verified) |
| `/accounts/:id`     | Account overview — balance chart, paginated transactions, send/exchange modals          |
| `/transactions/:id` | Transaction detail — full breakdown with PDF export                                     |
| `/kyc`              | Identity verification — name/country/document number, then ID photo + selfie upload     |

NgRx manages all state. The store has three feature slices:

- **accounts** — user's account list, loading state; balances also patch live via
  `accountBalanceUpdated`, dispatched by `notification.service.ts` off the SSE stream (see
  [Live balance updates](#live-balance-updates)) rather than only on an explicit reload
- **account-detail** — current account, transactions (infinite scroll), balance history, exchange rates (cached in store for the session), totalIn/totalOut stats, operation loading state
- **transaction** — single transaction detail

---

## Production Deployment (AWS)

The Terraform provisions all four backend services as separate ECS Fargate services, matching
the local architecture: the ALB/CloudFront only ever reach **gateway-service**; it reaches
identity-service, core-banking-service, and notification-service internally over **ECS Service
Connect** (DNS names `identity-service`/`core-banking`/`notification-service`, see
`infrastructure/terraform/ecs.tf`), the AWS equivalent of gateway proxying to
`localhost:8081`/`localhost:8082`/`localhost:8083` locally.

### Prerequisites

- AWS CLI configured (`aws configure --profile bankapp`)
- Terraform >= 1.10 (the S3 backend's native `use_lockfile` locking needs it)
- Docker
- Maven 3.9+, Java 21
- A real SMTP account/relay (e.g. Amazon SES) — identity-service sends real OTP emails in prod
  (`EmailOtpClient`, `@Profile("prod")`), unlike the mocked OTP used everywhere else

### Bootstrap remote state

`infrastructure/terraform/` stores its state in S3, but that bucket has to exist _before_
`terraform init` can point at it - a classic chicken-and-egg problem. `infrastructure/terraform-state/`
is a separate, tiny root module (its own state stays local) that just creates that bucket;
`bootstrap-state.sh` applies it and then initializes the main config against the bucket it just
created:

```bash
./infrastructure/scripts/bootstrap-state.sh
```

Run this once per create/destroy cycle, before the first `terraform plan`/`apply` below. If you
tear the whole stack down and expect to spin it back up later, run
`./infrastructure/scripts/teardown-state.sh` instead of `terraform destroy` directly - it destroys
the main config first, then the state bucket, so nothing outlives the stack it belonged to. (A
long-lived team deployment would instead create this bucket once, keep it forever, and skip this
script entirely - see the comments in `infrastructure/terraform-state/main.tf`.)

### Provision infrastructure

```bash
cd infrastructure/terraform
cp terraform.tfvars.example terraform.tfvars   # fill in mail_*
terraform plan
terraform apply
```

(`terraform init` already happened as part of `bootstrap-state.sh` above.)

`terraform apply` creates: VPC, subnets, NAT gateway, ALB (300s idle timeout, for
notification-service's open SSE connections), ECS cluster + 5 Fargate services
(identity-service, core-banking-service, gateway-service, notification-service, and a
`debit-eligibility-mock` running the same WireMock stub as local dev - see
[Debit eligibility](#debit-eligibility)) all wired together with Service Connect, 2 RDS PostgreSQL
instances (one per service that owns data), a single-instance DocumentDB cluster
(notification-service's message store - see `docdb.tf`), ElastiCache Redis (shared by every
service except the WireMock-based `debit-eligibility-mock`), an S3 bucket for KYC document/selfie
uploads (+ CORS), 5 ECR repos, CloudFront + S3 for the Angular frontend, per-service IAM roles,
and Secrets Manager secrets (DB passwords, JWT secret, mail credentials, Mongo connection string).

Two RDS instances (rather than one) matches the "one DB per service" rule the application itself
enforces — see Architecture. Consolidating into a single instance with two logical databases is a
valid cost optimization for a real deployment, at the cost of extra Terraform complexity (a second
provider to manage databases inside the instance).

### Deploy

```bash
# Deploy one service (build JAR -> Docker image -> ECR -> force ECS redeployment)
./infrastructure/scripts/deploy-service.sh identity
./infrastructure/scripts/deploy-service.sh core-banking
./infrastructure/scripts/deploy-service.sh gateway
./infrastructure/scripts/deploy-service.sh notification
./infrastructure/scripts/deploy-service.sh debit-eligibility-mock  # no Maven build - just packages backend/wiremock

# Deploy frontend (ng build -> S3 sync -> CloudFront invalidation)
./infrastructure/scripts/deploy-frontend.sh
```

All scripts read required values from `terraform output` automatically.

After deploy the app is available at the CloudFront URL:

```bash
cd infrastructure/terraform && terraform output app_url
```

**Known gap**: ECS has no built-in way to wait for a _dependent service's container_ to be
healthy before starting another (only for the AWS resource to exist) - core-banking's `DataSeeder`
calls identity-service over HTTP at boot and may crash-and-restart once or twice if
identity-service isn't ready yet, self-healing on ECS's automatic task restart. Same ordering
caveat as local dev - see "Running Locally" above.
