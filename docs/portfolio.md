# StockFlow — portfolio and CV wording

## CV project entry

**StockFlow | Java Backend Development | Personal Project**

[github.com/BoyanChakarov/stockflow](https://github.com/BoyanChakarov/stockflow)

**Technologies:** Java 21, Spring Boot, Spring Security, Spring Data JPA, PostgreSQL, Flyway, REST APIs, JUnit, Testcontainers, Docker, GitHub Actions.

- Built an inventory and order management application with Java 21 and Spring Boot, exposing validated REST APIs and a Thymeleaf interface with role-based access and CSRF protection.
- Implemented atomic stock reservations, PostgreSQL row locking, and persistent idempotency to handle competing orders and repeated requests while preserving inventory consistency.
- Verified transaction rollback, concurrent operations, and access controls with 38 passing JUnit tests and 82.5% Java line coverage; automated Docker startup, browser workflows, and restart persistence checks in GitHub Actions.

## Compact CV version

**StockFlow — Java 21 / Spring Boot / PostgreSQL** · [GitHub](https://github.com/BoyanChakarov/stockflow)

- Built an inventory/order application with transactional reservations, role-based security, and persistent request deduplication.
- Validated database races and rollback with JUnit/Testcontainers; 38 tests passed with 82.5% line coverage, alongside Docker and browser acceptance in CI.

## Portfolio project card

**Title:** StockFlow — Inventory & Order Management

**Category:** Java backend engineering · Personal project

An inventory and order management application built with Java 21, Spring Boot, and PostgreSQL. StockFlow focuses on stock consistency under concurrent orders, atomic database transactions, and safe request retries. It includes authenticated REST APIs, a responsive Thymeleaf interface, real PostgreSQL integration tests, and automated Docker/browser verification in GitHub Actions.

**Links:** [Source code](https://github.com/BoyanChakarov/stockflow) · [Release](https://github.com/BoyanChakarov/stockflow/releases/tag/v1.0.0) · [Demo walkthrough](demo.md) · [Architecture](architecture.md)

The demo runs locally with Docker Compose; there is no hosted public application.

## Interview walkthrough

Start with the stock invariant: `available = on_hand - reserved`, with `0 <= reserved <= on_hand`. Creating an order checks every line and commits the order, snapshots, reservations, and movement history in one transaction. If any line is unavailable, the whole operation rolls back.

Explain the concurrency choice next. PostgreSQL row locks protect stock checks and updates; product locks follow a consistent UUID order. Separate database connections test last-unit competition and opposing order transitions. Lock waits are bounded, and contention can produce a retryable response.

Then walk through a lost response. The server may have committed even though the browser did not receive the result. Retrying the same idempotency key and normalized payload returns the stored response without reserving stock again. The response and business writes share a transaction, so a failure rolls both back.

Finish with a tradeoff: a single service and database make these transaction boundaries explicit. Scaling to multiple application instances would require shared sessions or a different authentication design; higher write contention would need measurements before changing the locking strategy.

## Evidence behind the wording

| Claim | Implementation or verification |
| --- | --- |
| Atomic reservations and consistent lock ordering | [OrderService](../src/main/java/com/boyanchakarov/stockflow/order/OrderService.java), [locking decision](decisions/002-stock-locking.md) |
| Persistent request deduplication | [Idempotency](../src/main/java/com/boyanchakarov/stockflow/common/Idempotency.java), [idempotency decision](decisions/003-idempotency.md) |
| Role-based security and CSRF | [SecurityConfig](../src/main/java/com/boyanchakarov/stockflow/auth/SecurityConfig.java), `rolesAndCsrfAreEnforcedInHttpAndServices` in [StockFlowIT](../src/test/java/com/boyanchakarov/stockflow/StockFlowIT.java) |
| Concurrency and rollback verification | `lastUnitRaceWithDistinctCustomers`, `fulfilCancelRaceHasOneTerminalTransition`, and `thrownAfterFlushRollsBackAllWritesIncludingClaim` in [StockFlowIT](../src/test/java/com/boyanchakarov/stockflow/StockFlowIT.java) |
| 38 passing tests and 82.5% line coverage | [v1.0.0 verification record](verification.md) and [release CI](https://github.com/BoyanChakarov/stockflow/actions/runs/38237970588) |
| Docker, browser, and restart verification | [CI workflow](../.github/workflows/ci.yml), [browser acceptance](../browser-tests/workflow.mjs), [API smoke checks](../scripts/smoke.py) |

These measurements describe the verified v1.0.0 release. This is a personal project using synthetic data; the wording makes no claims about commercial employment, production adoption, measured performance gains, or customer impact.
