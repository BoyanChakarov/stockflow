# StockFlow Complete Application Specification and Implementation Guide

**Owner:** Boyan Chakarov  
**Target:** Java software engineering portfolio  
**Release target:** v1.0.0  
**Guide date:** 12 October 2026  
**Status:** Specification and implementation handoff

This is the single reference file for implementing StockFlow. It combines the full MVP scope with the concrete schema, API examples, transaction algorithms, UI work, tests, setup, and GitHub delivery requirements. Read the product rules first, then follow the implementation handoff in order. The supplied SQL and payloads are reference material to verify while building; no application or test result is being delivered by this document.

## Contents

- [Product boundaries](#product-boundaries)
- [Users and permissions](#users-and-permissions)
- [Functional requirements](#functional-requirements)
- [Order lifecycle and stock rules](#order-lifecycle-and-stock-rules)
- [Architecture and Java implementation](#architecture-and-java-implementation)
- [PostgreSQL data model](#postgresql-data-model)
- [Transactions and concurrency](#transactions-and-concurrency)
- [Duplicate-request behaviour](#duplicate-request-behaviour)
- [REST API contract](#rest-api-contract)
- [Browser screens](#browser-screens)
- [JUnit test strategy](#junit-test-strategy)
- [GitHub repository and reproducible delivery](#github-repository-and-reproducible-delivery)
- [Demonstration and development sequence](#demonstration-and-development-sequence)
- [Release acceptance checklist](#release-acceptance-checklist)
- [Technical references](#technical-references)
- [Implementation handoff](#implementation-handoff)
  - [Project bootstrap](#project-bootstrap)
  - [Configuration and environments](#configuration-and-environments)
  - [Concrete database migration](#concrete-database-migration)
  - [Entity and repository implementation](#entity-and-repository-implementation)
  - [DTOs and validation](#dtos-and-validation)
  - [Request and response examples](#request-and-response-examples)
  - [Idempotency implementation algorithm](#idempotency-implementation-algorithm)
  - [Implementing stock and order services](#implementing-stock-and-order-services)
  - [Query services and dashboard](#query-services-and-dashboard)
  - [UI implementation sequence](#ui-implementation-sequence)
  - [Automated test implementation](#automated-test-implementation)
  - [Containers and local commands](#containers-and-local-commands)
  - [CI and GitHub implementation](#ci-and-github-implementation)
  - [Operational boundaries and optional hosting](#operational-boundaries-and-optional-hosting)
  - [Implementation completion and handoff report](#implementation-completion-and-handoff-report)
- [Additional implementation references](#additional-implementation-references)

StockFlow is a proposed internal inventory and order-management application for a small business. Its first release lets staff manage customers, check stock, reserve products for an order, and fulfil or cancel the order while maintaining a reliable history.

The implementation uses Java, Spring Boot, PostgreSQL, and JUnit Jupiter. The release deliverable is a reproducible GitHub repository containing the application, tests, database migrations, API specification, container setup, CI workflow, and demonstration instructions. This scope defines the finished project; it does not represent an implementation or test results that already exist.

The portfolio objective is to demonstrate Java application design, relational modelling, transactions, concurrency, automated testing, and delivery. Completion means another developer can clone the repository, run the application, and verify the important business rules.

## Product boundaries

The MVP serves one business, one warehouse, and internal employees. Products are sold as whole units in EUR. A customer is a business record, not a login account. The application tracks orders and inventory; an order total is an operational quoted amount, not an invoice, tax calculation, or payment record.

| Included in the MVP | Excluded from the first release |
| --- | --- |
| Administrator and staff access | Public registration, password reset emails, social login |
| Product catalogue and stock balances | Multiple warehouses, multi-tenant organisations |
| Customer records | Customer shopping accounts and a public storefront |
| Confirm, fulfil, and cancel whole orders | Saved drafts, partial fulfilment, backorders, returns |
| Stock receipts and manual corrections | Supplier purchasing workflows, barcode hardware |
| Stock reservation and movement history | Expiring reservations and scheduled stock releases |
| Duplicate-request protection | Message queues and distributed event processing |
| Search, pagination, low-stock list | Forecasting, AI features, advanced reporting |
| REST API and a basic browser interface | Native mobile app or separate React application |
| Automated tests, Docker Compose, GitHub Actions | Kubernetes, multiple backend services, high availability |

A confirmed order keeps its reservation until a staff member fulfils or cancels it. Staff can find old open orders using the order list; there is no automatic expiry in this release.

The MVP runs as one Spring Boot application and one PostgreSQL database. Hosting a public demo is a useful later delivery option, but a GitHub upload alone does not host a Java backend.

## Users and permissions

The application has two roles. Both roles work on the same business records; staff can access all orders rather than only their own.

| Action | STAFF | ADMIN |
| --- | --- | --- |
| View products, available stock, and dashboard | Yes | Yes |
| Create, update, and view customers | Yes | Yes |
| Archive or reactivate customers | No | Yes |
| Create, view, fulfil, and cancel orders | Yes | Yes |
| View an order's history | Yes | Yes |
| Create, edit, archive, and reactivate products | No | Yes |
| Receive or adjust stock | No | Yes |
| View the complete stock movement history | No | Yes |

Use Spring Security form login with server-side sessions, BCrypt password hashing, and CSRF protection. Serve the UI and API from the same origin. Keep CSRF checks enabled for modifying requests, including login and logout. Spring Security provides protection for unsafe HTTP methods by default; the UI must pass the expected token [4].

Provide two accounts through an explicit local demo profile. Read their initial passwords from environment variables; commit only example variable names and setup instructions. A setup command or bootstrap profile creates the accounts on an empty database without resetting their passwords on every restart. Account administration screens are outside the MVP.

Browser pages redirect an unauthenticated user to login. API requests return JSON errors: 401 for missing authentication and 403 for insufficient permission or a missing/invalid CSRF token. Use secure, HttpOnly session cookies for HTTPS deployments. Restarting the single application may end active sessions; business data stays in PostgreSQL.

## Functional requirements

| ID | Feature | Required behaviour and acceptance |
| --- | --- | --- |
| F01 | Product catalogue | Create a product with unique SKU, name, description, unit price, and reorder threshold. Search by SKU/name, filter active state, and paginate results. Duplicate SKU is rejected. |
| F02 | Product editing | Change name, description, price, or threshold. Keep SKU immutable. Existing order snapshots and totals remain unchanged. Stock cannot be changed through a product-edit request. |
| F03 | Product archiving | Archive rather than delete. An archived product cannot appear in a new order; existing confirmed orders can still be fulfilled or cancelled. |
| F04 | Stock receipts | An administrator records a positive quantity and a required reason/reference. The balance and movement entry commit together. |
| F05 | Stock corrections | An administrator records a nonzero signed adjustment and reason. A reduction cannot make on-hand stock lower than reserved stock. Corrections use movement entries rather than overwriting balances. |
| F06 | Customers | Create and update name, optional contact information, and notes. Search and paginate. Customer names need not be unique. Archived customers cannot be used in new orders; historical orders remain available. |
| F07 | Order creation | Select an active customer and 1–50 distinct active products, each with a positive whole-number quantity. The server calculates prices and totals and reserves every line atomically. |
| F08 | Stock rejection | If any requested product lacks available stock, reject the entire order. No order, reservation, movement, or history entry remains. Return the affected product and current availability. |
| F09 | Order fulfilment | Change CONFIRMED to FULFILLED and reduce both on-hand and reserved quantities. Record the actor and timestamp. |
| F10 | Order cancellation | Change CONFIRMED to CANCELLED, release all reservations, and keep physical stock unchanged. Require a cancellation reason. |
| F11 | Order search | Filter by status, customer, and creation range. Show paginated results, stable sorting, quantities, quoted total, creator, and timestamps. |
| F12 | History | Show order transitions and inventory movements with actor, time, deltas, reason, and related order when applicable. No update/delete endpoints for history. |
| F13 | Duplicate-request protection | Repeating a successful order, receipt, or adjustment with the same actor, operation, key, and request body returns its stored result without repeating the mutation. |
| F14 | Dashboard | Show active product count, confirmed order count, low-stock product count, and a low-stock list. Label metrics precisely; order totals must not be called revenue. |
| F15 | Usable interface | Complete the catalogue-to-order workflow from the browser, with clear validation, empty states, success feedback, and permission-aware controls. Enforce permissions on the server too. |

All stock changes, successful order transitions, and their history entries occur in the same database transaction. History is an application audit trail, not a claim of tamper-proof storage.

Reject unknown request properties rather than silently accepting attempts to submit fields such as order status, price snapshots, reserved quantity, or created-by user. Derive the actor from the authenticated session.

## Order lifecycle and stock rules

Maintain two counters per product: on_hand and reserved. Calculate available as on_hand minus reserved instead of storing a third counter.

| Operation | On-hand change | Reserved change | Availability effect |
| --- | --- | --- | --- |
| Receive q units | +q | 0 | +q |
| Confirm order for q units | 0 | +q | -q |
| Fulfil that order | -q | -q | No further change |
| Cancel that order | 0 | -q | +q |
| Manual correction by delta | +delta | 0 | +delta, provided invariants hold |

Required invariants are on_hand >= 0, reserved >= 0, and reserved <= on_hand. Reserved stock must also equal the sum of quantities in all CONFIRMED order lines for that product. The latter is a cross-table business invariant checked in integration tests, not a simple database CHECK constraint.

The lifecycle has three states:

| Current state | Command | Outcome |
| --- | --- | --- |
| No order | Create and reserve | CONFIRMED if every line can be reserved |
| CONFIRMED | Fulfil | FULFILLED |
| CONFIRMED | Cancel | CANCELLED |
| FULFILLED | Fulfil again | Return the current order; no new stock/history entries |
| CANCELLED | Cancel again | Return the current order; preserve the original cancellation reason |
| FULFILLED | Cancel | 409 conflict |
| CANCELLED | Fulfil | 409 conflict |

Order contents are immutable after creation. To change quantities or the customer, cancel the confirmed order and submit a new one. No direct status-edit endpoint exists.

Example: a product starts with on_hand=10 and reserved=0. Confirming an order for 3 produces 10/3/7 for on-hand/reserved/available. Fulfilling produces 7/0/7; cancelling instead produces 10/0/10.

Store prices using BigDecimal and PostgreSQL NUMERIC. Accept at most two decimal places for unit prices, reject negative prices, and calculate line and order totals on the server. Set an explicit upper bound of EUR 999,999.99 per unit, 1,000,000 units per line, and 50 distinct lines per order to keep validation and arithmetic bounded. Zero-priced products are allowed; negative pricing is not. Use NUMERIC(12,2) for unit prices and NUMERIC(18,2) for totals.

Snapshot the SKU, product name, unit price, and customer name/contact on the order. Later catalogue or customer edits must not change its historical display. Use UTC timestamps and display their timezone.

## Architecture and Java implementation

Use Java 21, a stable Spring Boot release compatible with the selected dependencies, Maven Wrapper, Spring MVC, Spring Data JPA, Spring Security, Bean Validation, PostgreSQL, Flyway, and Thymeleaf. Pin exact versions in the delivered repository. Java 21 is within the compatibility range documented for Spring Boot 4.1.1 at specification time [1]. Let the Spring Boot dependency management select compatible JUnit Jupiter components rather than independently mixing major versions.

Use packages grouped by feature under com.boyanchakarov.stockflow: auth, catalog, customer, inventory, order, reporting, and common. The common package contains error handling and genuinely shared primitives.

REST controllers and Thymeleaf MVC controllers call the same application services. Put transaction boundaries and business rules in services. Repositories handle persistence. Request and response DTOs define the external contracts; do not serialize JPA entities directly.

Use constructor injection. Validate request shape at the boundary and enforce stock, state, and permission rules in the service/security layers. Return a consistent ProblemDetail-style error body with status, code, detail, field errors when applicable, and a request identifier. Avoid exposing stack traces, SQL, or passwords.

Set Hibernate schema handling to validate. Flyway owns schema creation and changes. Disable Open Session in View; fetch what the view or response needs inside the service transaction. Use paginated DTO projections or deliberate fetches to avoid an extra customer/items query per row in list views.

A static OpenAPI contract at docs/openapi.yaml is part of the MVP. An interactive Swagger UI is optional and may be added with a dependency verified to support the chosen Spring Boot version. The implemented API and the documented paths, DTOs, errors, and security must agree.

## PostgreSQL data model

Use UUID primary keys, foreign keys, NOT NULL constraints where appropriate, and TIMESTAMPTZ timestamps. Use VARCHAR plus explicit CHECK constraints for the small status/role sets so Flyway migrations remain straightforward.

| Table | Main fields | Constraints and purpose |
| --- | --- | --- |
| app_users | id, username, password_hash, role, enabled, created_at | Unique normalized username; role ADMIN or STAFF; no plaintext password |
| products | id, sku, name, description, unit_price, reorder_threshold, active, on_hand, reserved, created_at, updated_at | Unique uppercase SKU; nonnegative balances/threshold; reserved <= on_hand; product starts with zero stock |
| customers | id, name, email, phone, notes, active, created_at, updated_at | Required name; validate optional contact fields; archive instead of delete |
| orders | id, customer_id, customer snapshot fields, status, currency, total, notes, created_by, created_at, fulfilled_at, cancelled_at, cancellation_reason | Currency EUR; valid status; appropriate terminal timestamps; references to customer and user |
| order_items | id, order_id, product_id, sku_snapshot, name_snapshot, unit_price_snapshot, quantity, line_total | Positive quantity; unique pair order_id/product_id; nonnegative amounts |
| stock_movements | id, product_id, order_id nullable, actor_id, type, delta_on_hand, delta_reserved, on_hand_after, reserved_after, reason, created_at | Types RECEIPT, ADJUSTMENT, RESERVE, FULFIL, RELEASE; immutable through the application |
| order_events | id, order_id, actor_id, from_status nullable, to_status, reason nullable, created_at | One creation event and at most one terminal transition; no event for a harmless repeated terminal command |
| idempotency_records | id, actor_id, operation, key, request_hash, response_status, response_body, response_location nullable, created_at | Unique actor_id/operation/key; store completed successful results in the same transaction as their business operation |

Index the actual query paths: orders(status, created_at, id), orders(customer_id, created_at, id), order_items(order_id, product_id) through its unique constraint, order_items(product_id), stock_movements(product_id, created_at, id), and order_events(order_id, created_at, id). Add indexes for other referencing foreign keys where joins or maintenance require them; do not duplicate indexes already supplied by primary/unique constraints.

Start product/customer name search with bounded paginated case-insensitive matching. If a larger demo dataset makes substring search slow, measure with EXPLAIN ANALYZE before adding a trigram index. Do not claim an ordinary B-tree makes every substring query fast.

Preserve historical references with restrictive deletion rules. Product/customer archiving does not cascade into orders. Do not commit database volumes, dumps containing real information, or passwords to GitHub.

Provide versioned migrations such as V1__create_schema.sql and V2__add_query_indexes.sql. Keep demo seed data in an explicit demo initializer, separate from production schema migrations.

## Transactions and concurrency

Prevent overselling with PostgreSQL row locks acquired inside short service transactions. SELECT FOR UPDATE blocks conflicting modifications of the locked rows until the transaction ends [2]. Spring Data JPA pessimistic locking is an appropriate implementation route.

For creating an order:

1. Authenticate, authorize, and validate the request. Reject duplicate product IDs and invalid quantities.

2. Begin the transaction and claim the idempotency key.

3. Lock the customer row sufficiently to keep its active state and snapshot stable during creation; customer edits/archive must respect that lock.

4. Lock all requested product rows in ascending UUID order. Use a consistent database-side lock order across every inventory operation.

5. Recheck active status, prices, and available quantities under those locks.

6. If every line can be reserved, insert the order and immutable line snapshots, increment reserved counters, and add reservation movements and the order-created event.

7. Persist the successful response in the idempotency record, then commit everything together.

8. Send the response after the commit succeeds.

For fulfilment or cancellation, lock the order row first, then its product rows in the same ascending order. Validate the current order status after the order lock is acquired. Concurrent fulfil/cancel commands must result in one terminal transition and one valid set of stock changes.

Receipts, manual adjustments, product edits, and product archiving must also use the same product-row locking discipline. Never read availability outside a transaction and later write a new balance without rechecking under a lock.

Use the order: idempotency claim when applicable, customer when needed, existing order when needed, then products. Do not make external network calls while holding these locks. Configure a bounded lock timeout and roll back on timeout/deadlock; return a retryable 503 response without partial writes. A caller can retry with the original idempotency key.

Use PostgreSQL READ COMMITTED with these explicit locks for the MVP. Do not attempt to solve database races with only Java synchronized, a disabled browser button, or a mocked repository test.

## Duplicate-request behaviour

Require an Idempotency-Key UUID for POST /orders, POST /inventory/receipts, and POST /inventory/adjustments. Scope each key to the authenticated actor and canonical operation. The request fingerprint is a hash of the validated, normalized business payload; sort order lines by product ID and normalize decimal representations before hashing.

The browser generates one key for a deliberate submission and preserves it when retrying the same payload after a network failure. Changing the intended operation or editing the payload creates a new key.

| Situation | Result |
| --- | --- |
| New key and valid request | Commit once and store the successful response |
| Same actor, operation, key, and normalized payload | Return the original status/body/Location; do not recalculate prices or mutate stock |
| Same key scope but different payload after a successful commit | 409 IDEMPOTENCY_KEY_REUSED |
| Simultaneous identical requests | One mutation; both receive the stored successful result once available |
| Validation/business failure | No successful record retained; retry is evaluated again |
| Transaction failure before commit | Business changes and key claim roll back together |
| Commit succeeds but response is lost | Retry retrieves the committed successful result |

Use a database unique constraint with an insert-on-conflict strategy; avoid a separate check-then-insert race. A conflict may need to wait for the first transaction. On a bounded timeout, return 503 and allow retry. Do not catch a PostgreSQL uniqueness failure and continue using a transaction that is already aborted.

Keep successful keys for the life of the MVP dataset. There is no automatic cleanup/expiry policy. Authenticate and authorize every replay, and resolve a completed replay before performing current catalogue-availability checks. This protects a valid retry even if the original order has since been fulfilled or a product archived.

Fulfil and cancel are separately safe to repeat through row locking and terminal-state checks. Repeated successful commands produce no second stock movement or event.

## REST API contract

Prefix business endpoints with /api/v1. All business endpoints require a valid session; modifying requests also require a CSRF token. The browser can obtain a token from GET /api/v1/csrf before form login and refresh it after login. POST /login uses Spring Security form parameters; POST /logout ends the session.

| Method and path | Permission | Purpose |
| --- | --- | --- |
| GET /api/v1/csrf | Public | Obtain a CSRF token for the current session |
| GET /api/v1/me | STAFF or ADMIN | Return current username and role |
| GET /api/v1/products | STAFF or ADMIN | Search/filter products and stock |
| GET /api/v1/products/{id} | STAFF or ADMIN | Product and balance details |
| POST /api/v1/products | ADMIN | Create a product with zero stock |
| PATCH /api/v1/products/{id} | ADMIN | Edit catalogue fields or active flag |
| GET /api/v1/customers | STAFF or ADMIN | Search/filter customers |
| GET /api/v1/customers/{id} | STAFF or ADMIN | Customer details |
| POST /api/v1/customers | STAFF or ADMIN | Create a customer |
| PATCH /api/v1/customers/{id} | STAFF or ADMIN | Edit customer fields; active flag restricted to ADMIN |
| POST /api/v1/inventory/receipts | ADMIN | Add positive stock; requires idempotency key |
| POST /api/v1/inventory/adjustments | ADMIN | Apply signed correction; requires idempotency key |
| GET /api/v1/inventory/movements | ADMIN | Filter and paginate stock history |
| POST /api/v1/orders | STAFF or ADMIN | Create a confirmed order and reserve stock; requires key |
| GET /api/v1/orders | STAFF or ADMIN | Search/filter/paginate orders |
| GET /api/v1/orders/{id} | STAFF or ADMIN | Order, line snapshots, and totals |
| POST /api/v1/orders/{id}/fulfil | STAFF or ADMIN | Fulfil the whole order |
| POST /api/v1/orders/{id}/cancel | STAFF or ADMIN | Cancel with a required reason |
| GET /api/v1/orders/{id}/events | STAFF or ADMIN | Read order history |
| GET /api/v1/dashboard | STAFF or ADMIN | Counts and a limited low-stock list |

The order-create payload contains customerId, optional notes, and items containing productId and quantity. The API rejects client-supplied totals, prices, status, or actor.

Use 201 for successful resource creation, 200 for reads/updates/terminal commands, 400 for malformed input or failed validation, 401/403 for access failures, 404 for missing resources, 409 for stock/state/uniqueness/idempotency conflicts, and 503 for transient lock failures. A stored idempotent replay preserves its original successful status.

Pagination defaults to 20 and caps at 100. Use a whitelisted sort field with ID as a stable tiebreaker. Date filters use ISO-8601 UTC timestamps with inclusive from and exclusive to semantics. Distinguish missing resources from inactive resources; the latter are business conflicts when creating a new order.

Document representative success and error bodies in OpenAPI, including INSUFFICIENT_STOCK, INVALID_ORDER_TRANSITION, PRODUCT_INACTIVE, CUSTOMER_INACTIVE, DUPLICATE_SKU, IDEMPOTENCY_KEY_REUSED, and RETRYABLE_CONFLICT. An insufficient-stock error includes productId, requested quantity, and currently available quantity.

## Browser screens

Use Thymeleaf and simple responsive CSS to keep the Java backend central to the project. A separate frontend build pipeline is unnecessary for this release.

| Screen | Required controls and information |
| --- | --- |
| Login | Username/password, clear failed-login feedback |
| Dashboard | Product/open-order/low-stock counts, links to operational lists |
| Products | Search, active-state filter, pagination, on-hand/reserved/available columns |
| Product details and edit | Product information, admin edit/archive controls, receive/adjust forms |
| Customers | Search, create/edit forms, active-state display |
| Orders | Filters by status/customer/date, stable pagination |
| Create order | Select customer, add products, quantities, price preview, validation, final total |
| Order details | Frozen line values, status, actor/time history, fulfil/cancel actions |
| Stock history | Administrator filters by product/type/date and movement details |

The UI price/availability preview is advisory. The creation response contains the authoritative snapshot and total; display these after confirmation. If stock changed since the preview, show the conflict and preserve the entered lines so the user can correct them.

Buttons reflect permissions and valid transitions, while server checks remain authoritative. Require explicit confirmation before fulfilment or cancellation. Provide empty states, readable errors, labelled form controls, keyboard navigation, and layouts usable on laptop and narrow screens. Persist form idempotency keys across retries instead of generating a new key for every click.

## JUnit test strategy

Use JUnit Jupiter for unit and integration tests. Use Mockito selectively for isolated service collaborators, Spring MVC tests for HTTP/security behaviour, and Testcontainers with a real PostgreSQL instance for persistence, transactions, migrations, and concurrency. Spring Boot documents Testcontainers integration with both JUnit and SpringBootTest [3].

Configure Maven Surefire for *Test and Failsafe for *IT, with both integration-test and verify goals bound. The command ./mvnw clean verify must execute every required suite. Docker must be available for the PostgreSQL tests; they must fail clearly rather than silently skip.

| Test ID | Scenario | Required assertion |
| --- | --- | --- |
| T01 | Price multiplication and total calculation | Exact decimal results; client cannot override totals |
| T02 | Invalid quantities, excess scale, duplicate lines, oversized order | Rejected input and no mutation |
| T03 | Product/customer archive rules | New orders reject inactive records; historical orders remain usable |
| T04 | Price/customer edit after creation | Original order snapshots and total unchanged |
| T05 | Receipt and signed correction | Counters and movement deltas agree; a correction cannot consume reserved stock |
| T06 | Successful order | Full order, items, reservations, movements, event, and idempotency response commit together |
| T07 | One unavailable line in a multi-line order | Entire operation rolls back, including earlier available lines |
| T08 | Two requests competing for one available unit | Exactly one order succeeds; the other receives stock conflict; no negative balance |
| T09 | Multiple products requested in opposite input order | Deterministic locking produces correct balances without a lock-order deadlock |
| T10 | Fulfil and cancel race on the same order | Exactly one terminal transition and correct ledger/balances |
| T11 | Fulfil or cancel repeated | No double deduction/release and no duplicate history |
| T12 | Same idempotency key sequentially and concurrently | One order/receipt/adjustment; stored successful result returned |
| T13 | Same key with changed payload | Conflict and no second mutation |
| T14 | Replay after restart or after product archive | Committed stored response remains retrievable |
| T15 | Simulated failure after writes but before commit | Order, stock, history, and key record all roll back |
| T16 | Role and CSRF matrix | Unauthenticated requests fail; STAFF cannot mutate catalogue/stock or archive customers; missing CSRF fails |
| T17 | Fresh database and migrations | Application starts against an empty PostgreSQL database and schema validates |
| T18 | Search, pagination, error contract | Stable pages, allowed filters, correct codes/body shape, no entity/password leakage |
| T19 | Ledger reconciliation | Movement sums equal counters; reserved equals confirmed order-line quantities |
| T20 | Full browser workflow | Manual demo or browser automation proves login → receive → order → fulfil/cancel |

Concurrency tests use independent connections and committed transactions. Coordinate requests with latches/barriers and bounded waits; do not rely on arbitrary sleeps or place a barrier after a lock that blocks the other participant. Do not wrap the whole test in one rollback-only test transaction.

Save test reports and JaCoCo coverage in CI. Coverage is supporting evidence, not a substitute for the acceptance scenarios. Report the actual test count and coverage from the release run; do not promise a number in advance.

## GitHub repository and reproducible delivery

The intended repository name is stockflow, under BoyanChakarov. Treat that as a proposed destination until the repository actually exists. Keep the code, tests, documentation, and build configuration in one repository.

| Path | Required contents |
| --- | --- |
| README.md | Problem, screenshots, stack, quick start, test command, demo flow, links to detailed docs, current limitations |
| pom.xml and Maven Wrapper files | Pinned tool/dependency baseline and test lifecycle configuration |
| src/main/java/com/boyanchakarov/stockflow | Feature packages, controllers, DTOs, services, repositories, security |
| src/main/resources/db/migration | Flyway schema/index migrations |
| src/main/resources/templates and static | Thymeleaf views, CSS, small browser scripts |
| src/main/resources/application*.yml | Safe configuration defaults and local/test/demo profiles |
| src/test/java | Unit, HTTP/security, integration, and concurrency tests |
| docs/MVP_SCOPE.md | This agreed first-release scope |
| docs/openapi.yaml | Implemented API, payloads, authentication, and errors |
| docs/architecture.md | Modules, transaction boundaries, data model, dependency directions |
| docs/decisions | Short decisions on row locking, reservation semantics, idempotency, sessions |
| docs/testing.md | Test commands, Docker requirements, concurrency methodology |
| docs/demo.md | Repeatable demonstration and expected balances |
| docs/screenshots | Synthetic-data screenshots of the working application |
| Dockerfile and compose.yaml | Application and PostgreSQL, health checks, persistent named volume |
| .env.example and .gitignore | Configuration names/placeholders; exclude secrets, volumes, logs, target output |
| .github/workflows/ci.yml | Build, all test suites, coverage/report artifacts, container build |
| LICENSE | Explicit repository reuse terms; choose deliberately before publishing |

Expose the application on localhost:8080 for the local demo. Keep PostgreSQL on the Compose network unless local debugging explicitly requires host access. The application uses environment-provided database credentials and Flyway applies migrations on startup. A normal restart must retain data.

Document a fresh-clone workflow: configure .env from .env.example, run docker compose up --build, sign in with the configured demo credentials, and follow docs/demo.md. Document a separate local Java command and ./mvnw clean verify for developers. Warn only in the reset instructions that deleting the named volume destroys local demo data.

GitHub Actions runs on push and pull_request, sets up Java 21, runs the Maven verification command, uploads available test/coverage reports, and builds the application container. Use a runner with Docker for Testcontainers. GitHub documents Maven verification and artifact uploads as supported workflow patterns [5].

Keep the workflow read-only unless a separate release action needs publishing rights. Commit no secrets, real customer data, environment files, generated database state, or unverifiable performance claims. Pin the actual Java/container/dependency versions in the release and document them.

Create a v1.0.0 release only after the acceptance checklist passes. Include the release commit, screenshots/demo instructions, and test report links. A hosted public demo, domain name, and automatic deployment are optional and must be reported separately from publishing the source repository.

## Demonstration and development sequence

Use synthetic seed data: approximately 10 products, 3 customers, and two configured demo accounts. Include one low-stock product and one archived product. Seed opening inventory using the same receipt/movement logic as normal stock changes so the ledger reconciles.

The main demonstration:

1. Sign in as ADMIN and receive 10 units of a product.

2. Sign in as STAFF, select a customer, and create an order for 3 units.

3. Show on_hand=10, reserved=3, available=7.

4. Replay the create request with the same idempotency key; show the same order ID and unchanged balances.

5. Fulfil the order; show on_hand=7, reserved=0, available=7.

6. Create another order for 2 units and cancel it; show stock restored to the same 7 available units and the cancellation event.

7. Attempt to order 8 units and show rejection without partial writes.

8. Show the JUnit concurrency test where two buyers compete for one unit; only one succeeds.

9. Show that STAFF cannot record a receipt and that the API enforces this permission.

10. Open the GitHub Actions run and explain the real PostgreSQL integration tests.

Build in dependency order, with tests added alongside each feature:

| Milestone | Deliverable | Exit condition |
| --- | --- | --- |
| 1 | Project, PostgreSQL, Flyway, CI skeleton | Empty database starts; baseline test runs in CI |
| 2 | Security, products, customers | Role matrix and validation tests pass |
| 3 | Receipts, adjustments, history | Invariants and ledger reconciliation pass |
| 4 | Orders, snapshots, transitions | Reserve/fulfil/cancel and atomic rollback work |
| 5 | Idempotency and concurrent operations | Duplicate requests and competing orders pass real-DB tests |
| 6 | Browser workflow, docs, containers, release | Fresh-clone demo works; acceptance checklist passes |

A reasonable personal planning budget is about 4–6 focused weeks, with longer allowed when learning Spring Security, transaction handling, or testing from scratch. This is a planning estimate, not a delivery commitment. Finish each milestone before adding optional features.

## Release acceptance checklist

- [ ] A fresh clone starts through the documented Compose workflow.

- [ ] Database schema is created by Flyway and validated by Hibernate.

- [ ] ADMIN and STAFF have the documented permissions.

- [ ] Products and customers can be managed through the browser.

- [ ] Stock receipts/corrections update balances and history atomically.

- [ ] Orders reserve stock immediately and snapshot prices/customer details.

- [ ] Insufficient stock rejects an entire multi-line order.

- [ ] Simultaneous requests cannot oversell the last unit.

- [ ] Fulfilment and cancellation obey the lifecycle and remain safe to repeat.

- [ ] Order/receipt/adjustment retries cannot duplicate a committed operation.

- [ ] Restarting the application preserves orders, balances, and duplicate-request records.

- [ ] All required unit, HTTP/security, PostgreSQL integration, and concurrency tests execute in ./mvnw clean verify.

- [ ] GitHub Actions passes on the exact release commit.

- [ ] README, API contract, architecture decisions, test instructions, and demo steps match the implementation.

- [ ] Git history, screenshots, and reports use genuine work and synthetic data.

- [ ] No secrets or database volumes are committed.

- [ ] v1.0.0 points to the verified commit and records any remaining limitations.

After completion, this project can support CV claims about a Java/Spring Boot REST application, PostgreSQL transactions and row locking, inventory reservations, session-based authorization, idempotency, JUnit/Testcontainers testing, and GitHub Actions. Use only the features and results present in the released code. Claims about customers served, production uptime, traffic, or business improvements require separate evidence.

## Technical references

The product rules, scope, and implementation choices above are design decisions for this project. The following primary documentation supports the underlying framework/database capabilities.

1. [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html) — Java and build compatibility.

2. [PostgreSQL explicit locking](https://www.postgresql.org/docs/current/explicit-locking.html) — row locks, conflicting operations, and deadlocks.

3. [Spring Boot Testcontainers integration](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html) — integration with JUnit and Spring application tests.

4. [Spring Security CSRF protection](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) — CSRF handling for session-based browser applications.

5. [GitHub Actions with Maven](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven) — build/test workflows and report artifacts.


## Implementation handoff

Implement the agreed MVP above as a complete application. This section supplies the concrete decisions and sequence needed to turn the requirements into code. The source examples are reference designs to implement and verify; this file is documentation, not a compiled application.

The first implementation must retain the stated scope. Resolve routine naming and formatting decisions within these requirements. Do not add payment providers, AI integrations, microservices, or cloud accounts. Keep existing unrelated repository files and history intact.

Recommended baseline: Java 21, Spring Boot 4.1.1, PostgreSQL 18, Maven Wrapper, and the JUnit Jupiter/Testcontainers versions compatible with Spring Boot dependency management. These versions reflect the documentation checked on 12 October 2026. Before starting, resolve dependencies and confirm the baseline; if a patch update is required, record the chosen exact version in pom.xml and docs/decisions. Use the same PostgreSQL major version in local containers and integration tests. Pin container image digests for the release.

### Project bootstrap

Create a Maven project with groupId com.boyanchakarov, artifactId stockflow, Java release 21, and Spring Boot parent/build plugin. The main class is StockFlowApplication in com.boyanchakarov.stockflow. Commit mvnw, mvnw.cmd, and .mvn/wrapper assets.

Use the following dependencies with versions supplied by Boot where managed. A dependency change must be checked with the selected release's documentation; do not combine outdated Spring Boot 3 examples with Boot 4 module/package names.

| Dependency | Scope |
| --- | --- |
| org.springframework.boot:spring-boot-starter-webmvc | Main |
| org.springframework.boot:spring-boot-starter-data-jpa | Main |
| org.springframework.boot:spring-boot-starter-security | Main |
| org.springframework.boot:spring-boot-starter-validation | Main |
| org.springframework.boot:spring-boot-starter-thymeleaf | Main |
| org.springframework.boot:spring-boot-starter-flyway | Main |
| org.flywaydb:flyway-database-postgresql | Main |
| org.postgresql:postgresql | Runtime |
| org.springframework.boot:spring-boot-starter-actuator | Main |
| org.springframework.boot:spring-boot-starter-test | Test |
| org.springframework.boot:spring-boot-starter-webmvc-test | Test |
| org.springframework.boot:spring-boot-starter-security-test | Test |
| org.springframework.boot:spring-boot-testcontainers | Test |
| org.testcontainers:testcontainers-postgresql | Test |

The PostgreSQL Testcontainers module uses the testcontainers-postgresql artifact name in the current documentation. Prefer a PostgreSQL container managed as a Spring test bean so its lifetime follows the cached application context; an additional JUnit container extension is unnecessary for that approach. Add an explicit compatible BOM/version only when the chosen Boot release does not manage a required artifact.

Configure Surefire, Failsafe, and JaCoCo. Verify that *IT tests run during Maven verify, not only unit tests. Configure the Spring Boot plugin to produce target/stockflow.jar through a fixed Maven finalName. A production container must use that exact output.

Create the CI workflow immediately with the baseline smoke test. Add tests and application features together after this point.

### Configuration and environments

Create application.yml for common behaviour, application-demo.yml for synthetic accounts/data, and application-prod.yml for HTTPS cookie settings. Test configuration must use disposable PostgreSQL and must not read a developer's production connection string.

The common configuration must include:

- Database URL, username, and password from environment variables.
- spring.jpa.hibernate.ddl-auto=validate.
- spring.jpa.open-in-view=false.
- Flyway enabled with classpath:db/migration.
- UTC JDBC/time handling and Java Instant for persisted timestamps.
- Session timeout, for example 30 minutes.
- HttpOnly session cookies; SameSite=Lax; Secure=true in HTTPS deployments.
- Bounded JDBC pool size, initially 10.
- A 5-second PostgreSQL lock timeout for mutating transactions and a 15-second application transaction timeout.
- Health endpoint status only; do not publicly expose environment/configuration or detailed database diagnostics.
- Request correlation IDs and structured application logs.
- No request-body/password/cookie logging.

Use a parameterized set_config call with is_local=true inside each write transaction for lock_timeout, or another verified connection-safe equivalent. Do not leak a session-level timeout setting into pooled connections.

Use .env.example containing variable names and visibly replaceable example values: POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD, STOCKFLOW_ADMIN_PASSWORD, STOCKFLOW_STAFF_PASSWORD. Ignore the actual .env. Fail startup clearly when required secrets are absent; do not fall back to a hardcoded application password.

The demo bootstrap must run explicitly, create absent accounts once, and seed synthetic data once. Re-running must not duplicate stock receipts or reset changed account passwords. Run the seed inside a transaction and use fixed demo identifiers or a dedicated seed marker. Production profile must not invoke the demo bootstrap.

For authentication, expose GET /login and Spring Security POST /login form processing. The API uses the resulting session cookie. GET /api/v1/csrf returns token and headerName, and GET /api/v1/me returns userId, username, and role. Keep the session CSRF-token repository and issue a fresh token after login. Use a form login failure redirect for the UI; API access failures receive the documented JSON response.

Enable method authorization and enforce ADMIN permissions in application-service entry points in addition to URL rules. Bind user identity to the authenticated principal, not a body/header field chosen by the caller. A staff customer update containing active, even as part of a larger payload, must fail rather than silently changing archive state.

### Concrete database migration

The following is the V1 schema blueprint. Use application-generated UUIDs; no UUID extension is required. Add the indexes in V2 as shown later. Map names and data types consistently in JPA.

~~~sql
CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN', 'STAFF')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (username = lower(btrim(username))),
    CHECK (length(username) > 0)
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    unit_price NUMERIC(12,2) NOT NULL
        CHECK (unit_price BETWEEN 0 AND 999999.99),
    reorder_threshold BIGINT NOT NULL DEFAULT 0
        CHECK (reorder_threshold >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    on_hand BIGINT NOT NULL DEFAULT 0 CHECK (on_hand >= 0),
    reserved BIGINT NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (sku ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CHECK (length(btrim(name)) > 0),
    CHECK (reserved <= on_hand)
);

CREATE TABLE customers (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL CHECK (length(btrim(name)) > 0),
    email VARCHAR(254),
    phone VARCHAR(40),
    notes VARCHAR(2000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    customer_name_snapshot VARCHAR(200) NOT NULL,
    customer_email_snapshot VARCHAR(254),
    customer_phone_snapshot VARCHAR(40),
    status VARCHAR(16) NOT NULL
        CHECK (status IN ('CONFIRMED', 'FULFILLED', 'CANCELLED')),
    currency CHAR(3) NOT NULL DEFAULT 'EUR' CHECK (currency = 'EUR'),
    total NUMERIC(18,2) NOT NULL CHECK (total >= 0),
    notes VARCHAR(2000),
    created_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fulfilled_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    CHECK (
        (status = 'CONFIRMED'
         AND fulfilled_at IS NULL AND cancelled_at IS NULL
         AND cancellation_reason IS NULL)
        OR
        (status = 'FULFILLED'
         AND fulfilled_at IS NOT NULL AND cancelled_at IS NULL
         AND cancellation_reason IS NULL)
        OR
        (status = 'CANCELLED'
         AND fulfilled_at IS NULL AND cancelled_at IS NOT NULL
         AND cancellation_reason IS NOT NULL
         AND length(btrim(cancellation_reason)) > 0)
    )
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    sku_snapshot VARCHAR(64) NOT NULL,
    name_snapshot VARCHAR(200) NOT NULL,
    unit_price_snapshot NUMERIC(12,2) NOT NULL
        CHECK (unit_price_snapshot BETWEEN 0 AND 999999.99),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    line_total NUMERIC(18,2) NOT NULL CHECK (line_total >= 0),
    UNIQUE (order_id, product_id),
    CHECK (line_total = unit_price_snapshot * quantity)
);

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    order_id UUID REFERENCES orders(id),
    actor_id UUID NOT NULL REFERENCES app_users(id),
    type VARCHAR(16) NOT NULL
        CHECK (type IN ('RECEIPT', 'ADJUSTMENT', 'RESERVE', 'FULFIL', 'RELEASE')),
    delta_on_hand BIGINT NOT NULL,
    delta_reserved BIGINT NOT NULL,
    on_hand_after BIGINT NOT NULL CHECK (on_hand_after >= 0),
    reserved_after BIGINT NOT NULL CHECK (reserved_after >= 0),
    reason VARCHAR(500) NOT NULL CHECK (length(btrim(reason)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (reserved_after <= on_hand_after),
    CHECK (
        (type = 'RECEIPT' AND order_id IS NULL
         AND delta_on_hand > 0 AND delta_reserved = 0)
        OR
        (type = 'ADJUSTMENT' AND order_id IS NULL
         AND delta_on_hand <> 0 AND delta_reserved = 0)
        OR
        (type = 'RESERVE' AND order_id IS NOT NULL
         AND delta_on_hand = 0 AND delta_reserved > 0)
        OR
        (type = 'FULFIL' AND order_id IS NOT NULL
         AND delta_on_hand < 0 AND delta_reserved = delta_on_hand)
        OR
        (type = 'RELEASE' AND order_id IS NOT NULL
         AND delta_on_hand = 0 AND delta_reserved < 0)
    )
);

CREATE TABLE order_events (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    actor_id UUID NOT NULL REFERENCES app_users(id),
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (from_status IS NULL AND to_status = 'CONFIRMED')
        OR
        (from_status IS NOT NULL AND from_status = 'CONFIRMED'
         AND to_status IN ('FULFILLED', 'CANCELLED'))
    ),
    UNIQUE (order_id, to_status)
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES app_users(id),
    operation VARCHAR(40) NOT NULL
        CHECK (operation IN ('CREATE_ORDER', 'RECEIVE_STOCK', 'ADJUST_STOCK')),
    idempotency_key UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_status INTEGER,
    response_body JSONB,
    response_location VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    UNIQUE (actor_id, operation, idempotency_key),
    CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CHECK (
        (response_status IS NULL AND response_body IS NULL AND completed_at IS NULL)
        OR
        (response_status IS NOT NULL AND response_status BETWEEN 200 AND 299
         AND response_body IS NOT NULL AND completed_at IS NOT NULL)
    )
);
~~~

A newly claimed idempotency row is temporarily incomplete inside the same uncommitted transaction as the business operation. The transaction wrapper must finalize the response before returning; failure to produce a response throws and rolls back the claim. No code path may commit an incomplete row. Add a test that queries for incomplete committed records after every successful/failed scenario. There is no independently committed PROCESSING reservation.

The orders.total=sum(order_items.line_total) and products.reserved=sum(CONFIRMED order quantities) invariants span tables. Enforce them through the service transaction and reconciliation tests. Do not claim the scalar CHECK constraints enforce those sums.

Use V2__add_query_indexes.sql:

~~~sql
CREATE INDEX idx_orders_status_created
    ON orders(status, created_at DESC, id);
CREATE INDEX idx_orders_customer_created
    ON orders(customer_id, created_at DESC, id);
CREATE INDEX idx_orders_created_by ON orders(created_by);
CREATE INDEX idx_order_items_product ON order_items(product_id);
CREATE INDEX idx_movements_product_created
    ON stock_movements(product_id, created_at DESC, id);
CREATE INDEX idx_movements_order ON stock_movements(order_id);
CREATE INDEX idx_movements_actor ON stock_movements(actor_id);
CREATE INDEX idx_order_events_order_created
    ON order_events(order_id, created_at, id);
CREATE INDEX idx_order_events_actor ON order_events(actor_id);
CREATE UNIQUE INDEX uq_order_one_terminal_event
    ON order_events(order_id)
    WHERE to_status IN ('FULFILLED', 'CANCELLED');
~~~

The unique idempotency index already starts with actor_id, and the unique order-item constraint already starts with order_id; do not add redundant indexes for those leftmost prefixes. Database foreign keys intentionally have no destructive cascade. Set updated_at in the service on actual changes. Use Long for stock counters, Integer for per-line quantities, and Math.addExact/subtractExact where signed arithmetic may overflow.

### Entity and repository implementation

Create Product, Customer, AppUser, Order, OrderItem, StockMovement, OrderEvent, and IdempotencyRecord mappings. Use enums persisted as STRING and explicit column lengths/precision. Use lazy associations and restrict mutation methods rather than exposing unrestricted setters for stock/order state.

Model Order as the aggregate owning its line snapshots. Maintain its line collection in Java and persist it with suitable cascade PERSIST; there is no delete order endpoint or orphan-removal workflow in this MVP. Map item/product references without exposing product entities in the API.

Product should expose domain methods reserve(q), fulfil(q), release(q), receive(q), and adjust(delta), each checking the relevant invariant. These methods assume the service has obtained the required database lock. Make availableQuantity a computed getter.

Implement repository methods:

| Repository | Required operations |
| --- | --- |
| ProductRepository | Paginated filtering, findByIdForUpdate, existence/unique SKU checks |
| CustomerRepository | Paginated filtering, findByIdForUpdate |
| OrderRepository | Paginated summary projections, findByIdForUpdate, detail fetch |
| StockMovementRepository | Append and filtered/paginated history |
| OrderEventRepository | Append and ordered history |
| UserRepository | Find enabled account by normalized username |
| IdempotencyStore | Atomic claim, read completed result, complete claimed record |

A native single-product query can use SELECT * FROM products WHERE id=:id FOR UPDATE, or the repository can use JPA PESSIMISTIC_WRITE. Lock rows one at a time in ascending canonical UUID string order in every code path; with at most 50 lines, this is a clear initial implementation. Do not trust a collection-returning repository method to lock rows in the caller's list order.

Use the same single-row locking discipline for product metadata edits, receipts, adjustments, archiving, and order transitions. Product metadata updates use last-successful-write semantics; only submitted editable fields change. Do not copy a stale full entity over fresh stock counters.

Lock the customer row with PESSIMISTIC_WRITE during order creation and customer edits/archiving. This serializes concurrent orders for the same customer during short creation transactions, an acceptable MVP trade-off that simplifies snapshot consistency.

Use the default Spring JpaTransactionManager and the same DataSource for JPA and any JdbcTemplate work used by IdempotencyStore. Verify that both participate in the same transaction. Flush JPA before JDBC code that requires newly inserted JPA rows to be visible.

### DTOs and validation

Use Java records for API DTOs and explicit mappers. Do not accept a database entity as request data. Configure the selected JSON mapper to reject unknown properties, and test the setting with a forbidden field.

| Request | Accepted fields and limits |
| --- | --- |
| CreateProductRequest | sku 1–64 with the DDL pattern; name 1–200; description max 2000; unitPrice 0–999999.99 with <=2 decimals; reorderThreshold >=0 |
| UpdateProductRequest | Optional name, description, unitPrice, reorderThreshold, active; SKU and stock fields forbidden |
| CreateCustomerRequest | name 1–200; optional email max254 with valid email syntax, phone max40, notes max2000 |
| UpdateCustomerRequest | Same editable fields and optional active restricted to ADMIN |
| ReceiveStockRequest | productId UUID, quantity 1–1000000, reason 1–500 |
| AdjustStockRequest | productId UUID, delta -1000000..1000000 excluding 0, reason 1–500 |
| CreateOrderRequest | customerId UUID, items 1–50 distinct product IDs, each quantity 1–1000000, optional notes max2000 |
| CancelOrderRequest | reason 1–500 |

Trim human text and normalize SKU to uppercase and username to lowercase. An optional blank email/phone becomes null. Do not lowercase customer names. For PATCH, distinguish an absent field from an explicit null: absence preserves, null clears an optional field, null is invalid for required fields. Use a presence-aware patch DTO/mapping; a plain record with nulls cannot distinguish all three states. Reject an empty patch.

Expose monetary values as two-decimal strings in JSON. Request parsers must reject excess precision rather than round a price silently. In the browser, use integer cents for previews or return a server-calculated preview; display the stored server total after creation.

Paginated responses use content, page, size, totalElements, and totalPages. Product responses expose id, sku, name, description, unitPrice, active, reorderThreshold, onHand, reserved, available, and timestamps. Order summaries omit full line collections to avoid expensive list queries. Order details include snapshot customer, items, status, total, currency, creator, timestamps, and permitted actions if useful to the UI.

### Request and response examples

These are illustrative payloads. Replace UUIDs with records created by the demo or test setup. All business requests need the session cookie; mutations also need CSRF and the listed idempotency key.

POST /api/v1/products:

~~~json
{
  "sku": "MUG-BLUE",
  "name": "Blue ceramic mug",
  "description": "Demo product",
  "unitPrice": "12.50",
  "reorderThreshold": 5
}
~~~

POST /api/v1/inventory/receipts with a fresh Idempotency-Key:

~~~json
{
  "productId": "10000000-0000-4000-8000-000000000001",
  "quantity": 10,
  "reason": "Opening demo stock"
}
~~~

A successful receipt returns 201 and its movementId, productId, type, deltas, resulting counters, actor, and timestamp. GET /api/v1/inventory/movements?productId=... can retrieve it later; a Location header is optional for movement creation.

POST /api/v1/orders with a fresh Idempotency-Key:

~~~json
{
  "customerId": "20000000-0000-4000-8000-000000000001",
  "items": [
    {
      "productId": "10000000-0000-4000-8000-000000000001",
      "quantity": 3
    }
  ],
  "notes": "Collect from warehouse"
}
~~~

Successful response, 201 Created with Location: /api/v1/orders/{id}:

~~~json
{
  "id": "30000000-0000-4000-8000-000000000001",
  "status": "CONFIRMED",
  "customer": {
    "id": "20000000-0000-4000-8000-000000000001",
    "name": "Example Customer",
    "email": null,
    "phone": null
  },
  "items": [
    {
      "productId": "10000000-0000-4000-8000-000000000001",
      "sku": "MUG-BLUE",
      "name": "Blue ceramic mug",
      "quantity": 3,
      "unitPrice": "12.50",
      "lineTotal": "37.50"
    }
  ],
  "currency": "EUR",
  "total": "37.50",
  "notes": "Collect from warehouse",
  "createdBy": "40000000-0000-4000-8000-000000000001",
  "createdAt": "2026-10-12T10:00:00Z",
  "fulfilledAt": null,
  "cancelledAt": null,
  "cancellationReason": null
}
~~~

POST /api/v1/orders/{id}/cancel:

~~~json
{"reason": "Customer cancelled before collection"}
~~~

An insufficient-stock response has status 409 and application/problem+json:

~~~json
{
  "type": "about:blank",
  "title": "Insufficient stock",
  "status": 409,
  "detail": "The requested quantity exceeds available stock.",
  "code": "INSUFFICIENT_STOCK",
  "instance": "/api/v1/orders",
  "requestId": "example-request-id",
  "shortages": [
    {
      "productId": "10000000-0000-4000-8000-000000000001",
      "requested": 8,
      "available": 7
    }
  ]
}
~~~

Return all shortages discovered under the locks rather than only a generic error. Shortage availability describes the checked transaction state and may change after the response.

For 400 validation errors, include fieldErrors as a list of field/message pairs, using paths such as items[0].quantity. Security filters and controller advice must use the same error schema. Do not echo rejected passwords or full confidential payloads.

### Idempotency implementation algorithm

Put the transaction boundary around the idempotency wrapper and the business action together. One practical design is a non-transactional public facade invoking a TransactionTemplate callback; each HTTP entry calls it without an outer transaction. Return to the controller only after the callback has committed. All repositories and stock/history updates share that transaction.

Use canonical operation identifiers CREATE_ORDER, RECEIVE_STOCK, and ADJUST_STOCK. Hash the normalized, validated business DTO using SHA-256 and a deterministic serializer. Include every meaningful accepted field, distinguish null consistently, sort order items by canonical product ID, use fixed monetary scale, and exclude CSRF/session data. Store the result as lowercase hex.

Claim with a native SQL statement, bound parameters, and a generated record ID:

~~~sql
INSERT INTO idempotency_records
    (id, actor_id, operation, idempotency_key, request_hash)
VALUES
    (:id, :actorId, :operation, :key, :hash)
ON CONFLICT (actor_id, operation, idempotency_key) DO NOTHING
RETURNING id;
~~~

At READ COMMITTED, a conflicting insert waits for the competing transaction to settle. If no new ID is returned, use a new SQL statement in this transaction to read the committed record. Compare the fingerprint. On match, reconstruct the stored successful body/status/Location. On mismatch, return 409 without invoking the business action. A committed incomplete record indicates a programming/data error and must fail loudly instead of applying the mutation again.

If the claim succeeds, execute the business operation, map its successful DTO inside the transaction, flush pending JPA changes, then set response_status, response_body, response_location, and completed_at on the claim. Assert exactly one record was completed. Return only after commit. Store response data, not a live JPA entity.

Schema-level/basic input validation happens before claiming; current stock/product/customer eligibility checks happen only for a newly claimed request. This preserves successful replays after later business changes. Re-authenticate and authorize every retry.

A thrown business exception rolls back the new claim and all business changes. Do not catch a uniqueness exception and continue in an aborted transaction. Translate lock timeouts/deadlocks outside the transaction boundary to 503 RETRYABLE_CONFLICT with Retry-After: 1. The UI retries an uncertain submission only with its existing key. No automatic retry may generate a new key for the same intended operation.

~~~text
executeIdempotently(actor, operation, key, normalizedRequest, businessAction):
    check role and request syntax
    fingerprint = SHA256(canonical(normalizedRequest))
    return transaction.execute:
        set local lock timeout
        claim = insert-on-conflict
        if claim already exists:
            previous = read stored record
            verify same fingerprint and complete response
            return previous response
        result = businessAction()  // same transaction and DataSource
        flush all entity writes
        response = serialize successful result
        complete claim with response
        require completed claim
        return response
    // controller receives only after successful commit
~~~

A replay returns the original creation snapshot. If the order has subsequently been fulfilled, GET /orders/{id} returns the current state while the replay still returns the original CONFIRMED creation response. Document this difference and test it.

### Implementing stock and order services

InventoryService.receive validates ADMIN and positive quantity, claims its key, locks the product, adds on-hand stock, records a RECEIPT movement, and returns the movement plus resulting balance. Archived products may receive corrections/receipts; they simply cannot be ordered anew.

InventoryService.adjust validates ADMIN and signed delta, claims its key, locks the product, calculates a candidate on-hand value, and rejects it if below reserved or outside the supported integer range. Add one ADJUSTMENT movement with the supplied reason.

OrderService.create uses the idempotent transaction wrapper. Lock the customer, verify active, then lock products in canonical sorted order. Validate every line before mutating any counter. Calculate each line with unitPrice.multiply(BigDecimal.valueOf(quantity)); use exact scale 2. Build the snapshot data, order total, and generated order UUID. Insert the order and lines, reserve each product, record RESERVE movements and one creation event, and return the order detail DTO.

OrderService.fulfil uses a normal transaction. Lock the order, return it unchanged if already FULFILLED, and reject CANCELLED. Lock every associated product in the common order, check the reservation invariants, subtract each quantity from on_hand and reserved, append FULFIL movements, and set FULFILLED/fulfilled_at with one transition event. Keep cancellation fields null.

OrderService.cancel similarly locks the order. If already CANCELLED, return the current record without changing its original reason. Reject FULFILLED. Lock the products, reduce reserved only, append RELEASE movements, and set CANCELLED/cancelled_at/cancellation_reason with one event. Keep fulfilled_at null.

If a required reservation is unexpectedly missing, treat this as an integrity failure and roll back. Do not silently recreate reservations or clamp counters to zero.

Set domain timestamps from an injected Clock for deterministic tests. In a single business command, use one instant for its associated records. Same-instant rows are ordered by a stable tiebreaker in list responses; balances are validated by deltas and invariants, not by assuming UUIDs encode chronological order.

### Query services and dashboard

Use database-side pagination; never load every record and paginate in Java. Default ordering for orders is createdAt descending then ID; support only explicit sort fields. For products, use SKU as the default order with ID as tiebreaker. Search text is trimmed and capped at 100 characters.

Use summary projections for order lists and fetch full lines only for order details. Batch needed related data where appropriate. Test SQL/query behaviour with representative data to catch the N+1 pattern. A response should never trigger lazy database queries after leaving a service transaction.

DashboardService returns counts plus the first 10 active low-stock products. Low stock means available <= reorder_threshold. Sort the list by available ascending and SKU. Confirmed order count includes all CONFIRMED orders; active product count excludes archived products. If stronger consistency across the dashboard is required, use one query or a read-only repeatable-read transaction. The dashboard is informational and never the authority for reservation decisions.

### UI implementation sequence

Build common Thymeleaf layout fragments for navigation, feedback, form fields, pagination, and tables. Then implement login, product list/detail, customers, order creation/detail/list, dashboard, and stock history.

Suggested routes and templates:

| Browser route | Template |
| --- | --- |
| GET /login | auth/login.html |
| GET / | dashboard.html |
| GET /products | products/list.html |
| GET /products/new | products/form.html |
| GET /products/{id} | products/detail.html |
| GET /products/{id}/edit | products/form.html |
| GET /customers | customers/list.html |
| GET /customers/new and /customers/{id}/edit | customers/form.html |
| GET /orders | orders/list.html |
| GET /orders/new | orders/form.html |
| GET /orders/{id} | orders/detail.html |
| GET /inventory/movements | inventory/movements.html |

Use separate MVC controllers for HTML and REST controllers for JSON. They must call the same business services. Render receive/adjust forms on product details. HTML form handlers may adapt form data into the same command DTOs; never duplicate stock or order rules in a controller.

For order creation/receipt/adjustment, generate a UUID when a user starts a submission, retain it with the exact pending payload in sessionStorage, and reuse it after network uncertainty. Clear it only after a definite completed result or an explicit edit/new operation. Prevent accidental double clicking as a convenience; the backend remains responsible for deduplication.

Get the current CSRF header name/token from the server, include it in fetch mutations, and renew it after login. Use browser session cookies naturally; no tokens in localStorage. A session expiry must show a login prompt instead of treating an HTML login page as a JSON success.

Provide field-level errors and a summary, preserve entered values on rejected requests, and refresh availability after a stock conflict. Disable invalid terminal actions and explain the current state. Confirm fulfil/cancel actions with the order number and quantity context. The visible order reference is its UUID; a sequential human order number is outside this MVP.

### Automated test implementation

Create unit tests for money/normalization/domain rules and integration tests for actual PostgreSQL behaviour. Mockito does not prove that row locks or rollbacks work.

Use a test configuration with a PostgreSQL Testcontainers bean and @ServiceConnection or another verified Boot-supported connection configuration. Manage its lifecycle with Spring so the application context cannot reuse a stopped container. Match the PostgreSQL image to Compose. Run Flyway against every fresh test database.

Use test fixtures that create accounts, customers, and products through known setup helpers. Initial stock must reconcile with the movement ledger. Isolate scenarios by cleaning tables in foreign-key order between tests or using dedicated schemas/containers. Do not run destructive cleanup in parallel with another scenario.

For concurrency tests:

1. Prepare and commit the fixture outside the competing transactions.
2. Start two workers, each obtaining its own transaction/connection.
3. Synchronize their start before either takes a product/order lock.
4. Use bounded Future.get timeouts and collect both outcomes.
5. Query committed final state through a fresh connection.
6. Assert order count, status, balances, ledger entries, and reservation totals.
7. Always shut down executor resources.

For the last-unit scenario, use two distinct customers to avoid customer locking being the only serialization mechanism. Both actors order the same product with different keys. Assert one success and one INSUFFICIENT_STOCK result. For same-key concurrency, use the same actor and operation and assert one mutation with matching replay responses.

Include a scenario with two products requested in reversed input order. Include a fulfil/cancel race, an adjustment/create race, and a receipt retry. Keep repetition bounded and failures diagnostic; do not replace deterministic coordination with a long stress loop.

To verify rollback after writes, use a test-only injected hook/collaborator that throws a RuntimeException after reservation/history writes but before response finalization. Assert zero committed partial effects and no successful idempotency row. Do not add a public failure-injection endpoint.

Map acceptance IDs F01–F15 and T01–T20 to concrete test class/method names in docs/testing.md. Add any missing tests for the additional implementation details, such as absent versus null patch fields and CSRF-token renewal.

Enable CI reports for test failures as well as successes. Use meaningful assertions about business outcomes; no production gate should be satisfied merely by a collection of getter/setter tests.

### Containers and local commands

Use a multi-stage Docker build: compile the Maven project with Java 21, then copy target/stockflow.jar into a Java 21 runtime image. Run as a non-root user. It is acceptable for the image build to skip tests because the separate CI verify stage already ran them; a skipped image-build test phase must not replace that stage.

Compose must define app and db, a persistent db-data named volume, a PostgreSQL pg_isready health check, and a healthy-database dependency for app startup. App port 8080 maps to host 127.0.0.1:8080 for the local demo. Do not expose the database port in the normal Compose file.

For PostgreSQL 18, follow the official image's volume/PGDATA layout. Set an explicit PGDATA below the mounted /var/lib/postgresql directory and use the same documented layout in local and release instructions. Do not accidentally reuse the old major version's volume path or mount an incompatible existing database directory.

Document these steps in README:

~~~bash
cp .env.example .env
# Edit .env and set the required local passwords.
docker compose up --build
~~~

On Windows PowerShell, use Copy-Item .env.example .env; Maven commands use .\mvnw.cmd. Docker commands remain the same.

Full verification with Java 21 and a running Docker engine:

~~~bash
./mvnw clean verify
~~~

Normal stop preserving data:

~~~bash
docker compose down
~~~

Destructive demo reset, clearly labelled in the README:

~~~bash
docker compose down --volumes
~~~

For development outside the app container, provide a compose.dev.yaml override that exposes PostgreSQL only at 127.0.0.1:5432, set SPRING_DATASOURCE_URL accordingly, and run ./mvnw spring-boot:run with the demo profile. The standard Compose run must not require this override.

Add an application health check that verifies Spring Boot has finished starting and the database is reachable. If using Actuator, expose only /actuator/health with status information; keep other actuator endpoints protected.

### CI and GitHub implementation

Create .github/workflows/ci.yml with push and pull_request triggers, read-only contents permission, a bounded job timeout, Java 21 setup, Maven dependency caching, ./mvnw --batch-mode clean verify, report uploads using always(), and a container build after successful verification. Use supported, pinned action revisions and an Ubuntu runner with Docker available.

Reports should include target/surefire-reports, target/failsafe-reports, and target/site/jacoco. Build the image under a local CI tag without automatically publishing it. Use unique database state per run; never point CI at a real application's database.

GitHub delivery is complete only when all application source, tests, migrations, docs, and workflow are committed and pushed to the intended repository, and the workflow passes on that exact commit. Do not commit .env, credentials, database volumes, real customer information, logs, or target build output.

Before creating a repository, inspect the existing remote. If the project already has a remote, retain it unless a change was requested. If stockflow already exists under BoyanChakarov, inspect and reconcile it rather than overwriting unrelated contents or force pushing. Choose repository visibility explicitly when publishing; the public portfolio goal does not justify accidentally exposing private project data.

Use actual feature commits and descriptive messages; do not manufacture historical commits or collaboration. Create v1.0.0 after the final acceptance pass. Link the release to its CI run and document any known limitations.

The README must show: what the app solves; a screenshot; the exact stack; quick start; configured demo accounts; the full test command; the critical concurrency/idempotency scenarios; architecture decisions; and a short demo script. A reviewer should be able to get useful evidence without reading every source file.

### Operational boundaries and optional hosting

Local reproducibility and GitHub publication are mandatory. A public internet deployment is optional. GitHub Pages cannot execute this Java/PostgreSQL application; choose a Java/container host with persistent PostgreSQL if a hosted demo is later requested.

For an internet demo, use HTTPS, secure cookies, unique credentials, least-privilege runtime access, persistent database storage, and a documented backup/restore process. Keep synthetic data only and define how demo resets happen. Do not advertise production use, high availability, or real customer impact without evidence.

Before real business use, address account lifecycle, backups/restores, database migrations during upgrades, operational monitoring, privacy requirements, and recovery from stock/data corrections. These are rollout decisions beyond the portfolio release, not unimplemented promises hidden in the MVP.

### Implementation completion and handoff report

Complete the milestones in order, keeping the application runnable after each one. At the end, provide a concise implementation report containing:

- Repository URL, branch, and exact release commit.
- Implemented requirements and any explicitly incomplete ones.
- Commands actually run and their results.
- Actual unit/integration test count and coverage from the report.
- Evidence for last-unit, rollback, terminal-state race, and duplicate-request scenarios.
- How to start, sign in, stop, and reset the demo.
- GitHub Actions run URL and release link.
- Known limitations, with no claimed users/benchmarks that were not measured.

Do not mark a milestone complete when only interfaces, placeholder services, mocked persistence, or unexecuted tests exist. A clean build is necessary but does not demonstrate the required transactional behaviour by itself.

The application is ready for the CV when a fresh clone works, the complete verification suite passes, the important behaviours can be explained from the code, and the documentation accurately describes the released implementation.

## Additional implementation references

- [Spring Boot build systems and starters](https://docs.spring.io/spring-boot/reference/using/build-systems.html)
- [Spring Boot SQL and JPA configuration](https://docs.spring.io/spring-boot/reference/data/sql.html)
- [Spring Boot database initialization and Flyway](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Testcontainers PostgreSQL module](https://java.testcontainers.org/modules/databases/postgres/)
- [PostgreSQL Docker image documentation](https://hub.docker.com/_/postgres)


