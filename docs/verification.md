# Verification record

Recorded during implementation on 12 October 2026. The source release and its exact CI run are linked in the GitHub release notes.

- Java compilation, Flyway migration and Hibernate schema validation executed against isolated PostgreSQL 18.0.
- Local JUnit verification uses the explicitly selected external-database mode because this Windows workstation initially has neither Docker nor WSL. This is real PostgreSQL, not H2 or mocked persistence.
- Chromium desktop and 390px mobile checks executed, including lost-response recovery after a successful order commit. Screenshots are synthetic-data captures of the running application.
- OpenAPI 3.1 validated with openapi-spec-validator 0.7.2.
- Default Testcontainers verification, container build, fresh Compose workflow, browser acceptance and process-restart persistence are release gates in GitHub Actions.

The final test counts, coverage and exact-commit Actions URL belong to the verified release report, not to a planned estimate. A v1.0.0 tag must not be created while any required CI acceptance gate is failing.

No hosted internet application, production data, users, uptime or throughput claims are included.
