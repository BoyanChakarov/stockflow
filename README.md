# StockFlow · Java Inventory & Order Management

[![Verify StockFlow](https://github.com/BoyanChakarov/stockflow/actions/workflows/ci.yml/badge.svg)](https://github.com/BoyanChakarov/stockflow/actions/workflows/ci.yml)

**Java backend portfolio project by [Boyan Chakarov](https://github.com/BoyanChakarov)**

StockFlow manages products, customers, inventory, and orders for a small internal team. Its central engineering problem is keeping stock correct when users place competing orders, retry requests, or fulfil and cancel the same order concurrently.

Built with **Java 21, Spring Boot, Spring Security, Spring Data JPA, and PostgreSQL**, with a working Thymeleaf interface and a reproducible Docker demo. One warehouse, whole units, EUR quoted totals; all demonstration records are synthetic.

[Run locally](#quick-start) · [Demo walkthrough](docs/demo.md) · [Architecture](docs/architecture.md) · [Verified release](https://github.com/BoyanChakarov/stockflow/releases/tag/v1.0.0) · [CV wording](docs/portfolio.md)

## Engineering highlights

- **Transactional order processing:** reservations, order snapshots, stock movements, and events commit together. A shortage on any line rolls back the entire order.
- **Concurrency control:** PostgreSQL row locks are acquired in a consistent product order. Tests exercise competing last-unit orders and fulfil/cancel races using separate database connections.
- **Persistent request deduplication:** an actor-scoped idempotency key and normalized payload hash identify retries. The original successful response commits with the business changes and remains replayable after an application restart.
- **Security and validation:** session authentication, BCrypt passwords, ADMIN/STAFF authorization, CSRF protection, validated DTOs, and consistent API errors with request IDs.
- **Repeatable delivery:** Flyway migrations, container builds, PostgreSQL integration tests, browser acceptance, and restart checks run in GitHub Actions.

## Verified results

Results for [v1.0.0](https://github.com/BoyanChakarov/stockflow/releases/tag/v1.0.0), commit `2657dd2`, from its [successful CI run](https://github.com/BoyanChakarov/stockflow/actions/runs/38237970588):

| Check | Result |
| --- | --- |
| JUnit tests | **38 passed**: 6 unit + 32 PostgreSQL integration; none skipped |
| Java line coverage | **82.5%**: 924 of 1,120 lines, measured by JaCoCo |
| Docker Compose | Image build and healthy startup against an empty database passed |
| Chromium workflow | Role permissions, order lifecycle, lost-response retry, and mobile layout passed |
| Persistence | API smoke and application restart checks passed |

Coverage measures the Java code exercised by JUnit; browser checks are a separate acceptance gate. [Full verification record](docs/verification.md).

![StockFlow dashboard showing stock availability and confirmed orders](docs/screenshots/dashboard.png)

## Review the Java implementation

| Area | Start here | What to look for |
| --- | --- | --- |
| Domain and transactions | [OrderService](src/main/java/com/boyanchakarov/stockflow/order/OrderService.java), [Product](src/main/java/com/boyanchakarov/stockflow/catalog/Product.java) | Atomic reservation, immutable order snapshots, stock invariants, terminal transitions |
| Retry handling | [Idempotency](src/main/java/com/boyanchakarov/stockflow/common/Idempotency.java), [WriteTransactions](src/main/java/com/boyanchakarov/stockflow/common/WriteTransactions.java) | Unique database claim, payload fingerprint, stored response, bounded lock waits |
| Relational design | [Flyway migrations](src/main/resources/db/migration), [OrderQueries](src/main/java/com/boyanchakarov/stockflow/order/OrderQueries.java) | Constraints, indexes, parameterized queries, pagination, summary projections |
| Application security | [SecurityConfig](src/main/java/com/boyanchakarov/stockflow/auth/SecurityConfig.java), [OpenAPI contract](docs/openapi.yaml) | Session/CSRF configuration, role boundaries, documented request and response models |
| Failure and race testing | [StockFlowIT](src/test/java/com/boyanchakarov/stockflow/StockFlowIT.java), [acceptance map](docs/testing.md) | Concurrent transactions, rollback after flush, ledger reconciliation, HTTP authorization |

The application is a **single Spring Boot service organized by business feature**. Controllers exchange DTOs; application services own use cases; JPA and JDBC share PostgreSQL transactions. Flyway owns the schema, Hibernate validates it, and Open Session in View is disabled. See the [architecture](docs/architecture.md) and [design decisions](docs/decisions).

### Design tradeoffs

- **One service and one database** keep multi-product reservations inside a single transaction. Distributed deployment is outside this project's scope.
- **Pessimistic row locking** makes stock checks and updates atomic. Consistent lock ordering reduces deadlock risk; bounded waits produce a retryable API error under contention.
- **Same-origin sessions and Thymeleaf** fit an internal tool and keep authentication and deployment compact. Sessions are local to one application instance.
- **JPA plus targeted JDBC queries** combine aggregate persistence with explicit idempotency SQL and efficient order summaries. Throughput has not been benchmarked.

## Quick start

Requirements: Docker Engine/Desktop with Linux containers and Docker Compose v2. The container build includes Java and Maven; a host JDK is unnecessary for this path.

```bash
git clone https://github.com/BoyanChakarov/stockflow.git
cd stockflow
cp .env.example .env
# Edit .env: replace all three password placeholders with your own local values.
docker compose up --build --wait
```

On Windows PowerShell, use `Copy-Item .env.example .env` instead of `cp`. Open [localhost:8080](http://localhost:8080). Sign in as `admin` with `STOCKFLOW_ADMIN_PASSWORD`, or `staff` with `STOCKFLOW_STAFF_PASSWORD`. Passwords are BCrypt-hashed on first initialization and **are not reset by restarting** or changing the environment later. The explicit `demo` profile creates 10 products, three customers, and two accounts once. Blue ceramic mug starts at zero stock for the demonstration.

The app binds only to `127.0.0.1:8080`; PostgreSQL stays inside the Compose network. Database readiness and application/database health checks are included. Data persists in the `db-data` named volume.

```bash
docker compose down              # Stop, preserving data
docker compose up --wait         # Start again
```

**Destructive demo reset:** `docker compose down --volumes` deletes the named volume and all local demo records. Use this only when intentionally starting over.

## What is implemented

- ADMIN manages products, archiving, receipts, signed corrections, and complete stock history.
- ADMIN and STAFF manage customers and create, fulfil, cancel, and inspect all orders. Only ADMIN archives/reactivates customers.
- Catalogue/customer search, active-state filters, pagination, stock counters, date/status/customer order filters, and dashboard low-stock metrics.
- Atomic reservations across up to 50 distinct products; any shortage rejects the entire order.
- Immutable order line prices/names/SKUs and customer contact snapshots. Later edits preserve historical display.
- Duplicate order, receipt, and correction requests return their original successful result. A key with a different normalized payload conflicts.
- CSRF-protected form login/logout and REST API; server-side sessions; unknown input fields rejected; consistent errors with request IDs.
- Responsive Thymeleaf screens with permission-aware controls and retry state in `sessionStorage`, including recovery after a lost successful response.

## Stack and verification

Java 21, Spring Boot **4.1.1**, Spring MVC, Spring Data JPA/Hibernate, Spring Security, Bean Validation, Flyway, Thymeleaf, and PostgreSQL **18.0**. Maven Wrapper **3.9.11**, JUnit Jupiter/Testcontainers versions managed by the exact Boot BOM, Surefire/Failsafe **3.5.4**, JaCoCo **0.8.14**. PostgreSQL and Temurin container digests and GitHub Action commit SHAs are pinned. See [baseline decisions](docs/decisions/001-baseline.md).

With Java 21 and a running Docker engine:

```bash
./mvnw --batch-mode clean verify
# Windows: .\mvnw.cmd --batch-mode clean verify
```

Surefire runs unit tests; Failsafe runs PostgreSQL, HTTP/security, rollback, and concurrency integration tests. Docker absence fails the default suite instead of skipping it. Reports: `target/surefire-reports`, `target/failsafe-reports`, `target/site/jacoco/index.html`.

The concurrency suite coordinates independent committed transactions for last-unit competition, reverse product ordering, fulfil/cancel races, adjustment/create races, and identical idempotency keys. Every business integration test reconciles movement deltas and confirmed reservations. [Testing and acceptance map](docs/testing.md).

GitHub Actions runs the full suite, publishes reports, builds and starts the Compose stack from an empty database, executes a Chromium browser workflow and API smoke test, restarts the app, and verifies persisted balances/idempotency. [Workflow runs](https://github.com/BoyanChakarov/stockflow/actions/workflows/ci.yml).

## Local Java development

```bash
docker compose -f compose.yaml -f compose.dev.yaml up -d db
# Export POSTGRES_USER, POSTGRES_PASSWORD, STOCKFLOW_ADMIN_PASSWORD,
# STOCKFLOW_STAFF_PASSWORD from your local .env into the shell.
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/stockflow
export SPRING_PROFILES_ACTIVE=demo
./mvnw spring-boot:run
```

Maven does not automatically load `.env`; Docker Compose does. In PowerShell use `$env:NAME='your-local-value'` and `.\mvnw.cmd spring-boot:run`. If `POSTGRES_DB` differs, change the JDBC database name too. The development override exposes PostgreSQL only on loopback port 5432.

## Explore

- [Project summary, CV bullets, and interview talking points](docs/portfolio.md)
- [Seven-stage implementation plan](docs/IMPLEMENTATION_PLAN.md)
- [Scope and original implementation specification](docs/MVP_SCOPE.md)
- [Architecture and transaction boundaries](docs/architecture.md)
- [OpenAPI 3.1 contract](docs/openapi.yaml)
- [Ten-step demonstration](docs/demo.md)
- [Testing, browser automation, and acceptance mapping](docs/testing.md)
- [Verification record and release checks](docs/verification.md)

## Boundaries

This MVP has no public registration, password reset, account administration, payments, invoices/tax, partial fulfilment, returns, multiple warehouses, or reservation expiry. An order's quoted total is not revenue. There is no public hosted backend; GitHub publishes the source. In-memory sessions may end on restart; business records remain in PostgreSQL.

`prod` enables Secure cookies and excludes demo initialization. A real rollout also needs account provisioning/lifecycle, backup/restore procedures, operational monitoring, and deployment-specific HTTPS/database access controls. No production users, traffic, uptime, or performance claims are made.

MIT license · Boyan Chakarov
