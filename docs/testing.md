# Testing and acceptance map

## Full verification

Install Java 21 and run a Docker engine supporting Linux containers, then `./mvnw --batch-mode clean verify` (`.\mvnw.cmd` on Windows). Docker absence is an error, never a skipped suite. Surefire includes `*Test`; Failsafe binds integration-test and verify for `*IT`. Testcontainers is a Spring-managed PostgreSQL 18.0 bean whose lifetime follows the application context. Flyway runs and Hibernate validates every new test database.

Tests clean only their dedicated database tables, in isolated non-parallel scenarios, never a developer's normal database. Concurrency uses separate worker threads/connections, a latch before any locks, 20-second Future timeouts and executor cleanup. Last-unit requests use distinct customers. There are no arbitrary sleep-based race assertions.

Reports: `target/surefire-reports`, `target/failsafe-reports`, `target/site/jacoco`. Coverage is supporting evidence; no artificial percentage gate replaces the scenarios below.

## Explicit external PostgreSQL development mode

This mode runs the **same** real-database tests when Docker is unavailable; it does not prove container startup. It must be deliberately selected. Use only a disposable, dedicated database whose JDBC URL ends with `/stockflow_test`:

```bash
export STOCKFLOW_TEST_URL=jdbc:postgresql://127.0.0.1:5432/stockflow_test
export STOCKFLOW_TEST_USER=your_disposable_test_user
export STOCKFLOW_TEST_PASSWORD=your_disposable_test_password
./mvnw -Dstockflow.test.external=true clean verify
```

Test configuration rejects the normal application datasource unless these dedicated settings are supplied. The fixture truncates test tables; do not point it at business data.

## Browser acceptance

Optional Node tooling is kept under `browser-tests/`; it is not an application/frontend build dependency. Start a fresh Compose demo with no existing orders (the seeded mug must have zero stock), export the two demo passwords, then run from the repository root:

```bash
npm ci --prefix browser-tests
npm exec --prefix browser-tests -- playwright install chromium
node browser-tests/workflow.mjs
```

`STOCKFLOW_BROWSER_PATH` can select an installed Chromium-compatible executable. Screenshots and the result JSON go to `target/`. This verifies real form login, ADMIN receipt, STAFF permissions, order creation, server-committed response loss and retry after reload, fulfil/cancel, shortage feedback/value preservation, customer create/edit, and a 390px mobile layout. No JS page errors are allowed. CI runs it against the newly built Compose stack, then API smoke and a real application restart.

## Requirement-to-evidence mapping

All methods below are in `StockFlowIT` unless specified.

| Requirement | Evidence |
| --- | --- |
| F01, F02 | duplicateSkuAndMissingRecordsHaveSpecificErrors; presenceAwarePatchAndImmutableFields; laterEditsDoNotChangeSnapshots |
| F03, F06 | archivedRecordsRejectNewOrdersButExistingCanFulfil; presenceAwarePatchAndImmutableFields; browser customer workflow |
| F04, F05 | correctionsRespectReservationsAndLedger; receiptsAndAdjustmentsReplaySequentiallyAndConcurrently |
| F07, F08 | exactTotalsSnapshotsAndAtomicCreation; insufficientLineRollsBackWholeOrderAndKey; allShortagesAreReported |
| F09, F10, F12 | fulfilIsSafeToRepeatAndCancelConflicts; cancelRepeatsPreserveOriginalReason; fulfilCancelRaceHasOneTerminalTransition |
| F11, F14 | paginationFiltersDatesAndDashboardAreAccurate |
| F13 | concurrentDuplicateOrderCommitsOnlyOnce; normalizedLineOrderAndWhitespaceReplay; replayAfterFulfilAndArchiveReturnsOriginalConfirmedSnapshot |
| F15 | browser-tests/workflow.mjs and screenshots; rolesAndCsrfAreEnforcedInHttpAndServices |
| T01 | exactTotalsSnapshotsAndAtomicCreation; zeroPriceIsAllowedAndOutOfBoundsPricesAreRejected; unknownFieldsAndInvalidQuantitiesAreRejected |
| T02 | duplicateAndOversizedOrdersAreRejected; malformedAndForbiddenCommandFieldsNeverMutate; presenceAwarePatchAndImmutableFields |
| T03, T04 | archivedRecordsRejectNewOrdersButExistingCanFulfil; laterEditsDoNotChangeSnapshots |
| T05, T06 | correctionsRespectReservationsAndLedger; exactTotalsSnapshotsAndAtomicCreation |
| T07 | insufficientLineRollsBackWholeOrderAndKey |
| T08 | lastUnitRaceWithDistinctCustomers |
| T09 | reverseInputOrderCannotDeadlock |
| T10 | fulfilCancelRaceHasOneTerminalTransition; adjustmentCreateRacePreservesStock |
| T11 | fulfilIsSafeToRepeatAndCancelConflicts; cancelRepeatsPreserveOriginalReason |
| T12 | concurrentDuplicateOrderCommitsOnlyOnce; receiptsAndAdjustmentsReplaySequentiallyAndConcurrently; concurrentAdjustmentReplaysOnceAndOperationScopesAreIndependent |
| T13 | changedPayloadKeyConflictsAndActorScopeIsIndependent; concurrentAdjustmentReplaysOnceAndOperationScopesAreIndependent |
| T14 | replayAfterFulfilAndArchiveReturnsOriginalConfirmedSnapshot; scripts/smoke.py --check-restart after real process restart |
| T15 | thrownAfterFlushRollsBackAllWritesIncludingClaim; failedBusinessRequestCanRetrySameKey |
| T16 | rolesAndCsrfAreEnforcedInHttpAndServices; realFormLoginRenewsCsrfAndLogoutRequiresToken |
| T17 | DatabaseIT.freshSchemaMigratesAndHibernateValidates; fresh Compose CI stage |
| T18 | paginationFiltersDatesAndDashboardAreAccurate; duplicateSkuAndMissingRecordsHaveSpecificErrors; malformedAndForbiddenCommandFieldsNeverMutate |
| T19 | @AfterEach reconcile checks counters, confirmed reservations, order totals and incomplete claims after every business integration scenario |
| T20 | browser-tests/workflow.mjs; scripts/smoke.py; docs/screenshots |

Additional unit evidence: `ProductTest` tests reserve/fulfil/release, corrections, invalid operations and long overflow. `TextTest` verifies canonical normalization and a known SHA-256 vector. `stockLockTimeoutIsRetryableAndLeavesNoClaim` proves HTTP 503/Retry-After with an independently held database row lock and no committed claim.
