# Octopus Bank

A full-stack banking application. Supports multi-currency accounts, real-time balance tracking, currency exchange, cross-user transfers, and identity/KYC verification.

---

## Architecture

Three Spring Boot services behind an API gateway, plus the Angular SPA:

```
                         ┌────────────────────┐
  Browser ──────────────▶│   gateway-service   │  :8080  (Spring Cloud Gateway)
  (Angular, proxies      │  routes by path      │
   /api → :8080)         └──────────┬───────────┘
                                     │
                  ┌──────────────────┼──────────────────┐
                  │ /api/auth/**                          │ everything else
                  │ /api/kyc/**                            │ (/api/accounts/**,
                  ▼                                        ▼  /api/exchange-rates)
       ┌────────────────────┐                  ┌─────────────────────────┐
       │  identity-service   │  :8081           │  core-banking-service    │  :8082
       │  Users, Auth, JWT   │◀── internal API ──│  (this repo's "backend")│
       │  issuance, 2FA/OTP, │    (KYC status,   │  Accounts, transfers,   │
       │  KYC                │     user lookup)  │  exchange               │
       └─────────┬───────────┘                  └──────────┬───────────────┘
                  │                                          │
             identitydb (H2/Postgres)                 corebankingdb (H2/Postgres)
```

- **identity-service** owns `User`, registration/login, JWT issuance, 2FA/OTP, and KYC (document
  submission + verification status). It's the only service with a Users table. `TokenBlacklist`
  (revoked JWTs) and `OtpStore` (2FA challenges) are Redis-backed, not local maps.
- **core-banking-service** (this repo's original `backend`) owns `Account`/`Operation` and never
  looks a user up by ID — it trusts the JWT identity-service issued (shared signing secret,
  verified statelessly, no DB round-trip) and denormalizes the owner's public UUID + username
  directly onto `Account` at creation time. Account creation and outgoing transfers call
  identity-service's internal KYC endpoint (circuit-breaker wrapped, fails closed) before
  proceeding. `IdempotencyStore` (Idempotency-Key deduplication) is Redis-backed too.
- **gateway-service** is a thin Spring Cloud Gateway reverse proxy: `/api/auth/**` and
  `/api/kyc/**` go to identity-service, everything else under `/api/**` goes to core-banking.
  It's the only service the frontend (or a browser) ever talks to directly. It also rate-limits
  every request, **Redis-backed** (`RedisRateLimiter`), keyed by client IP + route id (see
  `RateLimiterConfig` — the key must include the route id, since `RedisRateLimiter`'s Redis key
  comes purely from the key resolver, with no route id mixed in on its own side; two routes
  resolving to the same key would silently share one token bucket). `/api/auth/login` gets a
  strict limit (burst 5, then a ~1/sec trickle) since it's the actual brute-force surface;
  everything else is looser (burst 20, ~5/sec trickle). If Redis itself is unreachable, Spring
  Cloud Gateway fails **open** — requests are allowed through rather than the whole gateway going
  down — verified locally by stopping Redis and confirming traffic still flows.

### Why everything moved to Redis

Every piece of in-memory state in this app (`TokenBlacklist`, `OtpStore`, `IdempotencyStore`, and
the gateway's rate limiter) has the same failure mode if its owning service ever runs more than
one instance behind a load balancer: state fragments per-instance. A revoked token would only be
blocked by whichever instance handled the logout; a 2FA challenge created on one instance
wouldn't be verifiable on another; two concurrent retries of the same Idempotency-Key could land
on different instances and both execute a non-idempotent transfer. Redis gives every instance of
a service one shared view of that state, the same fix already applied to the gateway's rate
limiter. All four were verified against a real Redis instance (not just mocks) - see each
class's own tests plus the behavior described in their code comments (single-use OTP challenges,
TTL-expired blacklist entries, no-duplicate-execution under a held lock, generic response types
round-tripping through `GenericJackson2JsonRedisSerializer` correctly).

`IdempotencyStore` is the one with real distributed-systems teeth: it uses a Redis
`SET NX PX`-style lock (`setIfAbsent` with a TTL) so that of two concurrent requests carrying the
same Idempotency-Key - even on different instances - only one executes the underlying operation;
the other polls for the result the first one writes (bounded wait, then a `503` asking the client
to retry, rather than silently double-executing a transfer). `TokenBlacklist` and `OtpStore` are
simpler: Redis's own key TTL replaces what used to be manually-checked expiry timestamps, and
`OtpStore`'s attempt counter uses `HINCRBY`, atomic server-side in Redis, so concurrent verify()
calls for the same challenge can't both slip past the attempt cap.

### Known simplifications (by design, for this project's scope)

- **Internal service-to-service endpoints** (`/internal/kyc/**`, `/internal/users/**` on
  identity-service) are unauthenticated and never routed through the gateway. A real deployment
  would put mTLS or a service-to-service credential on this trust boundary.
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

---

## Tech Stack

**Backend** — Java 21, Spring Boot 3.2, 3 services (see Architecture above)

| Concern       | Library                                                          |
| ------------- | ----------------------------------------------------------------- |
| Gateway       | Spring Cloud Gateway 2023.0 (routing, Redis-backed rate limiting) |
| Persistence   | Spring Data JPA, H2 (dev), PostgreSQL (prod) — one DB per service |
| Shared state  | Redis — rate limiting (gateway), token blacklist + OTP challenges |
|               | (identity-service), idempotency-key dedup (core-banking)          |
| Security      | Spring Security, JWT (jjwt 0.12) — issued by identity-service,    |
|               | verified statelessly everywhere else                              |
| Caching       | Spring Cache + Caffeine (5-min TTL on exchange rates)              |
| Resilience    | Resilience4j circuit breakers (debit eligibility, KYC status)      |
| Object storage| AWS SDK v2 (S3) — MinIO locally, real AWS S3 in prod, presigned    |
|               | uploads for KYC document/selfie photos (identity-service)          |
| Observability | Spring Actuator (`/actuator/health`) on every service              |
| Utilities     | Lombok, Bean Validation                                            |
| Tests         | JUnit 5, Mockito, Spring Boot Test, WireMock                       |

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
ECS Fargate -> 4 services (identity-service, core-banking-service, gateway-service,
  debit-eligibility-mock), wired together over ECS Service Connect
RDS PostgreSQL 16 -> one instance per service that owns data (identity, core-banking)
ElastiCache Redis -> shared by identity-service, core-banking-service, gateway-service
S3 -> KYC document/selfie uploads (identity-service), private + CORS-configured
ALB -> routes only to gateway-service (restricted to CloudFront IPs)
ECR -> 4 Docker image registries, one per service
Secrets Manager -> DB passwords, JWT secret, mail credentials

---

## Running Locally

Five things, started **in this order** (core-banking's `DataSeeder` calls identity-service over
HTTP at startup to look up the demo users it seeded, so identity-service must already be up and
finished seeding first; both of them - and the gateway - need Redis reachable before they start;
identity-service also needs MinIO reachable before it starts):

### 0. Redis + MinIO

All three backend services share one Redis instance (rate limiting, token blacklist/OTP,
idempotency-key dedup - see Architecture). Each fails in a different way if it's not there:
identity-service's login/logout/2FA endpoints will throw, core-banking's Idempotency-Key handling
will throw, but the gateway's rate limiter specifically fails **open** (see Architecture).

identity-service also stores KYC document/selfie uploads in S3-compatible object storage - MinIO
locally, real AWS S3 in prod (see the KYC section under "REST API Reference" below). Unlike
Redis, bucket creation is a hard startup dependency: `KycStorageInitializer` creates the bucket on
boot and fails fast (identity-service won't start) if it can't reach MinIO, the same fail-fast
choice as an unreachable database.

```bash
docker run -d --name bankapp-redis -p 6379:6379 redis:7-alpine
docker run -d --name bankapp-minio -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin \
  -e MINIO_API_CORS_ALLOW_ORIGIN=http://localhost:4200 \
  minio/minio server /data --console-address ":9001"
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
uploaded directly in the database, bypassing MinIO entirely, so MinIO only needs to be *reachable*
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

### 4. Frontend

```bash
cd frontend
npm install
npm start
```

---

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

| Method | Path                    | Description                                                           |
| ------ | ----------------------- | --------------------------------------------------------------------- |
| POST   | `/api/auth/register`    | Register `{username, email, password}` -> `{challengeToken}` (step 1 of 2FA) |
| POST   | `/api/auth/login`       | Login `{username, password}` -> `{challengeToken}` (step 1 of 2FA)    |
| POST   | `/api/auth/verify-otp`  | Verify OTP `{challengeToken, code}` -> `{token, userId, username}`    |
| POST   | `/api/auth/logout`      | Revoke the current token                                              |

### KYC (→ identity-service)

| Method | Path                                    | Description                                                                     |
| ------ | ---------------------------------------- | -------------------------------------------------------------------------------- |
| GET    | `/api/kyc/status`                        | Current user's KYC status/level/identity details/document upload state          |
| POST   | `/api/kyc/identity`                      | Submit identity `{firstName, lastName, issuingCountry, documentNumber}`         |
| POST   | `/api/kyc/documents/upload-url`           | Request a presigned upload URL `{type: ID_DOCUMENT\|SELFIE}` -> `{documentId, uploadUrl}` |
| POST   | `/api/kyc/documents/{documentId}/complete` | Confirm the upload landed in storage; auto-verifies once both are in           |

The document/selfie flow is presigned-URL-based: the browser gets a short-lived S3/MinIO PUT URL
from identity-service, uploads the file **directly** to storage (the bytes never pass through any
of our services), then calls `complete` - which does a real `headObject` check against storage
before trusting the upload, rather than taking the client's word for it. Verification itself is
still mocked (see Architecture's "Known simplifications"): once the identity form is submitted and
both an ID document photo and a selfie are confirmed uploaded, that auto-verifies to `BASIC`
instead of calling a real document/liveness vendor. Account creation and outgoing transfers on
core-banking both require `VERIFIED` status; the dashboard hides the "New Account" control until
then.

### Accounts (→ core-banking-service)

All account endpoints operate on the authenticated user's own accounts. Cross-user access returns 403.

| Method | Path                                             | Description                                                        |
| ------ | ------------------------------------------------ | -------------------------------------------------------------------- |
| POST   | `/api/accounts`                                  | Create account `{currency}` — requires KYC `VERIFIED`                |
| GET    | `/api/accounts/user/{userId}`                    | List all accounts for a user                                        |
| GET    | `/api/accounts/{id}`                             | Account summary (id, accountNumber, currency, balance)              |
| GET    | `/api/accounts/{id}/summary`                     | Account stats `{totalIn, totalOut}`                                 |
| GET    | `/api/accounts/{id}/balance-history`             | Balance over time (for chart)                                       |
| GET    | `/api/accounts/{id}/transactions?page=0&size=10` | Paginated transaction history                                       |
| GET    | `/api/accounts/transactions/{txId}`              | Single transaction detail                                           |
| POST   | `/api/accounts/{id}/exchange`                    | Exchange to another of your own accounts `{amount, targetAccountId}` |
| POST   | `/api/accounts/{id}/transfer`                    | Send to another user `{amount, targetUsername, targetAccountNumber, description}` — requires KYC `VERIFIED` |
| GET    | `/api/accounts/recipient?username=&accountNumber=` | Pre-submit recipient check `{valid}`, no account details leaked   |

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

| URL                 | Page                                                                                   |
| ------------------- | -------------------------------------------------------------------------------------- |
| `/login`            | Login / Register                                                                       |
| `/`                 | Dashboard — all accounts, currency totals, open new account (hidden until KYC verified) |
| `/accounts/:id`     | Account overview — balance chart, paginated transactions, send/exchange modals         |
| `/transactions/:id` | Transaction detail — full breakdown with PDF export                                    |
| `/kyc`               | Identity verification — name/country/document number, then ID photo + selfie upload   |

NgRx manages all state. The store has three feature slices:

- **accounts** — user's account list, loading state
- **account-detail** — current account, transactions (infinite scroll), balance history, exchange rates (cached in store for the session), totalIn/totalOut stats, operation loading state
- **transaction** — single transaction detail

---

## Production Deployment (AWS)

The Terraform provisions all three backend services as separate ECS Fargate services, matching
the local architecture: the ALB/CloudFront only ever reach **gateway-service**; it reaches
identity-service and core-banking-service internally over **ECS Service Connect** (DNS names
`identity-service`/`core-banking`, see `infrastructure/terraform/ecs.tf`), the AWS equivalent of
gateway proxying to `localhost:8081`/`localhost:8082` locally.

### Prerequisites

- AWS CLI configured (`aws configure --profile bankapp`)
- Terraform >= 1.10 (the S3 backend's native `use_lockfile` locking needs it)
- Docker
- Maven 3.9+, Java 21
- A real SMTP account/relay (e.g. Amazon SES) — identity-service sends real OTP emails in prod
  (`EmailOtpClient`, `@Profile("prod")`), unlike the mocked OTP used everywhere else

### Bootstrap remote state

`infrastructure/terraform/` stores its state in S3, but that bucket has to exist *before*
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

`terraform apply` creates: VPC, subnets, NAT gateway, ALB, ECS cluster + 4 Fargate services
(identity-service, core-banking-service, gateway-service, and a `debit-eligibility-mock` running
the same WireMock stub as local dev - see [Debit eligibility](#debit-eligibility)) all wired
together with Service Connect, 2 RDS PostgreSQL instances (one per service that owns data),
ElastiCache Redis (shared by all four), an S3 bucket for KYC document/selfie uploads (+ CORS), 4
ECR repos, CloudFront + S3 for the Angular frontend, per-service IAM roles, and Secrets Manager
secrets (DB passwords, JWT secret, mail credentials).

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
./infrastructure/scripts/deploy-service.sh debit-eligibility-mock  # no Maven build - just packages backend/wiremock

# Deploy frontend (ng build -> S3 sync -> CloudFront invalidation)
./infrastructure/scripts/deploy-frontend.sh
```

All scripts read required values from `terraform output` automatically.

After deploy the app is available at the CloudFront URL:

```bash
cd infrastructure/terraform && terraform output app_url
```

**Known gap**: ECS has no built-in way to wait for a *dependent service's container* to be
healthy before starting another (only for the AWS resource to exist) - core-banking's `DataSeeder`
calls identity-service over HTTP at boot and may crash-and-restart once or twice if
identity-service isn't ready yet, self-healing on ECS's automatic task restart. Same ordering
caveat as local dev - see "Running Locally" above.
