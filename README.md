# Octopus Bank

A full-stack banking application. Supports multi-currency accounts, real-time balance tracking and currency exchange.

---

## Tech Stack

**Backend** — Java 21, Spring Boot 3.2

| Concern       | Library                                                |
| ------------- | ------------------------------------------------------ |
| Persistence   | Spring Data JPA, H2 (dev), PostgreSQL (prod)           |
| Security      | Spring Security, JWT (jjwt 0.12)                       |
| Caching       | Spring Cache + Caffeine (5-min TTL on exchange rates)  |
| Resilience    | Resilience4j circuit breaker (debit eligibility check) |
| Observability | Spring Actuator (`/actuator/health`)                   |
| Utilities     | Lombok, Bean Validation                                |
| Tests         | JUnit 5, Mockito, Spring Boot Test, WireMock           |

**Frontend** — Angular 22 (standalone components, esbuild)

| Concern          | Library                                  |
| ---------------- | ---------------------------------------- |
| State management | NgRx Store 21 + Effects + Store Devtools |
| Charts           | Chart.js 4 + ng2-charts 10               |
| PDF export       | jsPDF 4                                  |
| Reactive         | RxJS 7.8                                 |
| Language         | TypeScript 6                             |
| Tests            | Jest 29 + jest-preset-angular            |

**Infrastructure** — AWS (Terraform 1.6+)

S3 + CloudFront -> Angular SPA
ECS Fargate -> Spring Boot container
RDS PostgreSQL 16 -> database
ALB -> backend routing (restricted to CloudFront IPs)
ECR -> Docker image registry
Secrets Manager -> DB password + JWT secret

---

## Running Locally

### Backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

API starts at **http://localhost:8080**. Uses H2 in-memory database.

- H2 Console at http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:bankdb`, user: `admin`, password: `admin123`)
- SQL query logging

On first run `DataSeeder` creates three demo users and seeds exchange rates.

### Frontend

```bash
cd frontend
npm install
npm start
```

---

## Authentication

All endpoints except `/api/auth/**` require a `Bearer` token in the `Authorization` header.

---

## REST API Reference

### Auth

| Method | Path                 | Description                                                           |
| ------ | -------------------- | --------------------------------------------------------------------- |
| POST   | `/api/auth/register` | Register `{username, email, password}` -> `{token, userId, username}` |
| POST   | `/api/auth/login`    | Login `{username, password}` -> `{token, userId, username}`           |

### Accounts

All account endpoints operate on the authenticated user's own accounts. Cross-user access returns 403.

| Method | Path                                             | Description                                             |
| ------ | ------------------------------------------------ | ------------------------------------------------------- |
| POST   | `/api/accounts`                                  | Create account `{currency}`                             |
| GET    | `/api/accounts/user/{userId}`                    | List all accounts for a user                            |
| GET    | `/api/accounts/{id}`                             | Account summary (id, accountNumber, currency, balance)  |
| GET    | `/api/accounts/{id}/summary`                     | Account stats `{totalIn, totalOut}`                     |
| GET    | `/api/accounts/{id}/balance-history`             | Balance over time (for chart)                           |
| GET    | `/api/accounts/{id}/transactions?page=0&size=10` | Paginated transaction history                           |
| GET    | `/api/accounts/transactions/{txId}`              | Single transaction detail                               |
| POST   | `/api/accounts/{id}/credit`                      | Deposit `{amount, description}`                         |
| POST   | `/api/accounts/{id}/debit`                       | Withdraw `{amount, description}`                        |
| POST   | `/api/accounts/{id}/exchange`                    | Exchange to another account `{amount, targetAccountId}` |

### Exchange Rates

Rates are stored in the database (EUR as pivot currency) and cached in memory for **5 minutes** (Caffeine).

| Method | Path                              | Description             |
| ------ | --------------------------------- | ----------------------- |
| GET    | `/api/exchange-rates`             | All rate pairs (cached) |
| GET    | `/api/exchange-rates/{from}/{to}` | Specific rate (cached)  |

Supported currencies and seeded rates:

| Currency | Rate to EUR |
| -------- | ----------- |
| EUR      | 1.00000000  |
| USD      | 0.92000000  |
| CHF      | 1.05000000  |
| GBP      | 1.17000000  |
| SEK      | 0.08700000  |
| VND      | 0.00003700  |

### Debit eligibility

Debit operations call an external eligibility service (`${debit.eligibility.url}/debit-eligibility/{userId}`). A Resilience4j circuit breaker wraps the call — if the service is down or times out, the debit is rejected (fail-closed).

---

## Frontend Pages

| URL                 | Page                                                                                   |
| ------------------- | -------------------------------------------------------------------------------------- |
| `/login`            | Login / Register                                                                       |
| `/`                 | Dashboard — all accounts, currency totals, open new account                            |
| `/accounts/:id`     | Account overview — balance chart, paginated transactions, credit/debit/exchange modals |
| `/transactions/:id` | Transaction detail — full breakdown with PDF export                                    |

NgRx manages all state. The store has three feature slices:

- **accounts** — user's account list, loading state
- **account-detail** — current account, transactions (infinite scroll), balance history, exchange rates (cached in store for the session), totalIn/totalOut stats, operation loading state
- **transaction** — single transaction detail

---

## Production Deployment (AWS)

### Prerequisites

- AWS CLI configured (`aws configure --profile bankapp`)
- Terraform >= 1.6
- Docker
- Maven 3.9+, Java 21

### Provision infrastructure

```bash
cd infrastructure/terraform
cp terraform.tfvars.example terraform.tfvars   # edit as needed
terraform init
terraform plan
terraform apply
```

`terraform apply` creates: VPC, subnets, NAT gateway, ALB, ECS cluster, RDS PostgreSQL, ECR repo, S3 bucket, CloudFront distribution, IAM roles, Secrets Manager secrets.

### Deploy

```bash
# Deploy backend (build JAR -> Docker image -> ECR -> force ECS redeployment)
./infrastructure/scripts/deploy-backend.sh

# Deploy frontend (ng build -> S3 sync -> CloudFront invalidation)
./infrastructure/scripts/deploy-frontend.sh
```

Both scripts read all required values from `terraform output` automatically.

After deploy the app is available at the CloudFront URL:

```bash
cd infrastructure/terraform && terraform output app_url
```
