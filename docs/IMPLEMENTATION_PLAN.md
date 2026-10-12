# StockFlow implementation stages

This plan was recorded before application implementation. The supplied StockFlow guide defines the requested product and acceptance criteria; the delivered code and test reports establish what actually works.

| Stage | Scope | Exit evidence |
| --- | --- | --- |
| 1. Foundation | Java 21 / Spring Boot 4.1.1, Maven Wrapper, PostgreSQL 18, Flyway, configuration, CI skeleton | Dependencies resolve; application compiles; empty schema migrates and validates |
| 2. Access and records | Session authentication, BCrypt, CSRF, role matrix, presence-aware PATCH, products/customers | HTTP/security and validation checks |
| 3. Inventory | Receipts, corrections, row locks, append-only ledger, bounded arithmetic | Stock and ledger reconciliation tests |
| 4. Orders | Atomic reservations, immutable snapshots, lifecycle, events | Whole-order rollback, snapshots, repeated transitions |
| 5. Reliability | Transactional idempotency, bounded lock waits, concurrency | Real PostgreSQL race/rollback/replay tests |
| 6. Browser workflow | Thymeleaf screens, accessible responsive CSS, filters, forms, resilient retries | Browser demo with ADMIN and STAFF, screenshots |
| 7. Delivery | OpenAPI, architecture/decisions, demo, containers, CI, public GitHub repository | Exact-commit CI, fresh Compose run, release only after acceptance |

## Delivery decisions

- Intended public repository: `BoyanChakarov/stockflow` (explicitly authorized in chat).
- License: MIT, permitting reuse of this portfolio implementation.
- One Spring Boot application and one PostgreSQL database; no separate frontend build.
- Tests use Testcontainers by default and fail if Docker is unavailable. An explicit external-test-database mode may run the same tests against an isolated real PostgreSQL database during development; it does not replace container/CI acceptance.
- No public internet application deployment is requested.

## Initial environment findings

Java 21 is installed. Maven, Docker, PostgreSQL, and WSL are not initially available on PATH. Build tools and an isolated development database will be prepared within the workspace. Docker/Compose verification and GitHub authentication will be tracked explicitly rather than claimed without evidence.


## Implementation outcome

All seven stages are complete. The final release commit `2657dd2acbd41913ba3a9a374a93a7e397a42b23` passed the unit, integration, container, fresh-database, browser, API, and restart acceptance gates in [GitHub Actions](https://github.com/BoyanChakarov/stockflow/actions/runs/38237970588) before [v1.0.0](https://github.com/BoyanChakarov/stockflow/releases/tag/v1.0.0) was published. The release notes and handoff report record that exact commit and run.

Delivered: all F01–F15 features, coverage for T01–T20, 38 passing JUnit tests with none skipped, a separate Chromium acceptance workflow, OpenAPI, architecture decisions, synthetic screenshots, digest-pinned Docker images, reproducible demo initialization, and public source publication. See `docs/testing.md` for the requirement map and `docs/verification.md` for measured evidence.
