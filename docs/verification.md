# Verification record

Recorded during implementation on 12 October 2026. The source release and its exact CI run are linked in the GitHub release notes.

- Java compilation, Flyway migration and Hibernate schema validation executed against isolated PostgreSQL 18.0.
- Local JUnit verification uses the explicitly selected external-database mode because this Windows workstation initially has neither Docker nor WSL. This is real PostgreSQL, not H2 or mocked persistence.
- Chromium desktop and 390px mobile checks executed, including lost-response recovery after a successful order commit. Screenshots are synthetic-data captures of the running application.
- OpenAPI 3.1 validated with openapi-spec-validator 0.7.2.
- Default Testcontainers verification, container build, fresh Compose workflow, browser acceptance and process-restart persistence are release gates in GitHub Actions.

## Executed release-candidate evidence

[Successful full Actions run](https://github.com/BoyanChakarov/stockflow/actions/runs/38237504562) on commit `6dec7f82c4cc67d3355145bbf638bd39884360f2`:

| Check | Result |
| --- | --- |
| Unit tests | 6 passed |
| PostgreSQL/Testcontainers integration tests | 32 passed |
| Failures / errors / skipped | 0 / 0 / 0 |
| JaCoCo Java lines | 924 covered / 1,120 total — 82.50% |
| JaCoCo branches | 165 covered / 260 total — 63.46% |
| JaCoCo instructions | 3,986 covered / 5,129 total — 77.71% |
| Container build and healthy fresh Compose startup | Passed |
| Chromium browser workflow, including committed response loss + reload/retry | Passed |
| ADMIN product create/edit/archive/reactivate and readable filter errors | Passed |
| API smoke and application restart persistence | Passed |
| Default database image | PostgreSQL 18.0, digest pinned |

Reports are available in that run's `verification-reports`, `browser-evidence`, and `stockflow-application` artifacts. Coverage describes Java exercised by JUnit; browser execution is a separate acceptance gate.

The release notes identify the final release commit and its own successful Actions run. The final documentation/log-format commit must pass these same gates before `v1.0.0` is created. No failed run is presented as release evidence.

No hosted internet application, production data, users, uptime or throughput claims are included.
