# Architecture

StockFlow is one Java 21 Spring Boot application and one PostgreSQL database. Spring MVC serves REST controllers and Thymeleaf pages from the same origin. The browser uses small native JavaScript form adapters; there is no frontend build requirement. Both controllers and pages obtain business data through the same services.

## Feature packages

| Package | Responsibility |
| --- | --- |
| auth | Database-backed users, BCrypt, sessions, CSRF, actor resolution, explicit demo bootstrap |
| catalog | Products, immutable SKUs, computed availability, bounded stock domain methods |
| customer | Customer records, presence-aware edits and administrative archiving |
| inventory | Receipts, signed adjustments, append-only movement history |
| order | Order aggregate and snapshots, reservation/terminal commands, summary queries, events |
| reporting | Dashboard counts and low-stock list |
| common | Transaction/idempotency wrappers, errors, request IDs, validation, paging, MVC composition |

Controllers accept request DTOs and return DTO records, never JPA entities. A presence-aware `Patch` accepts only an explicit field whitelist: absent preserves, null clears optional text, required null is invalid. Service validation applies to both browser and direct API commands. Business actors always come from the authenticated session.

## Persistence and invariants

Flyway owns the schema; Hibernate validates it with Open Session in View disabled. UUID keys and restrictive foreign keys retain historical references. PostgreSQL CHECK constraints enforce scalar stock, movement-shape, money, role and lifecycle limits. A partial unique index prevents a second terminal event. Order items are owned by the aggregate and cascade only persistence; no delete endpoints exist.

`available = on_hand - reserved`. Domain methods preserve `0 <= reserved <= on_hand` using checked arithmetic. Service transactions additionally preserve `reserved = sum(CONFIRMED line quantities)` and `order.total = sum(line totals)`. Integration tests reconcile these cross-table invariants and the movement ledger after every scenario.

Prices use BigDecimal and NUMERIC; responses expose two-decimal strings. The browser previews in integer cents using BigInt. Product name/SKU/price and customer name/contact snapshots freeze when the order is confirmed. The injected UTC clock ticks at microsecond precision, matching PostgreSQL timestamps.

## Write boundaries

`WriteTransactions` uses the JPA transaction manager, READ COMMITTED, a 15-second timeout and transaction-local `lock_timeout=5s`. JdbcTemplate and JPA share the DataSource/transaction. No network calls occur under row locks.

Lock acquisition order is: idempotency claim when applicable; customer for creation, or existing order for terminal commands; then product rows, one at a time, in canonical UUID-string order. Metadata edits and archiving lock the same product/customer rows. All lines are checked before reservation changes. Order, lines, counters, movements, events and successful idempotency response commit together. Controllers receive the result only after the transaction callback commits.

The order entity implements Persistable to identify newly assigned UUID aggregates correctly; this avoids treating newly constructed line snapshots as detached data during Spring Data save.

`Idempotency` inserts with `ON CONFLICT DO NOTHING RETURNING id`. A losing claimant reads the committed result in a separate statement, compares a SHA-256 hash of normalized DTO fields, and returns the stored status/body/Location. A successful retry is resolved before current stock/archive checks. A failed business transaction rolls back the claim. No committed PROCESSING records or cleanup policy exist. A test-only `BeforeCommitHook` can throw after the JPA flush to prove rollback; it has no endpoint and no production bean.

Fulfil/cancel lock the order and check terminal state. Repetition returns current state without another movement/event; opposing terminal commands conflict. Lock timeouts/deadlocks are translated outside the transaction to `503 RETRYABLE_CONFLICT`, with `Retry-After: 1`.

## Reads and security

Product/customer/movement filtering and pagination happen in PostgreSQL. Order list JDBC projections include quantity summaries without materializing line collections or issuing an extra network query per row. Detail DTOs initialize lines inside their read transaction. Only documented sort fields can enter SQL; all filter values are bound parameters. Literal `%`, `_` and backslash in searches are escaped.

Dashboard reads use a read-only REPEATABLE READ transaction. Low stock includes only active products whose available units are at or below the threshold, ordered by availability/SKU/ID, limited to ten.

Session cookies are HttpOnly and SameSite=Lax; `prod` adds Secure. CSRF remains enabled on all unsafe methods, including login/logout. API authentication and access errors share the ProblemDetail-style schema with controller errors. Request IDs are generated server-side, returned in headers, and added to logs. No request-body, cookie or password logging is enabled. Authority checks run in both HTTP routing and application services.

## UI retry behavior

Each deliberate order/receipt/adjustment submission saves `{key, body}` in sessionStorage before sending. Network uncertainty retains it; a reload restores the form. Editing business fields starts a new operation. Merely changing a search query does not discard an uncertain key. The server remains the deduplication authority. Current availability/price previews are advisory; the saved response is authoritative.

## Operational scope

One application instance with local HTTP sessions is intentional. The ledger is an application audit trail, not tamper-proof storage. PostgreSQL 18 data is mounted at `/var/lib/postgresql` with `PGDATA=/var/lib/postgresql/18/docker`. Container runtime is non-root. Only Actuator health status is exposed.
