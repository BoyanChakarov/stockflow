# ADR 001 — Pinned baseline

Accepted 12 October 2026. Use Java 21, Spring Boot 4.1.1, Maven 3.9.11, and PostgreSQL 18.0. The exact Boot parent manages compatible Hibernate, Spring Security, Jackson 3, JUnit Jupiter, Flyway, and Testcontainers components. Boot 4 starter/module names are used. Maven Wrapper 3.3.4 verifies the distribution SHA-256. Surefire/Failsafe 3.5.4 and JaCoCo 0.8.14 are explicit.

The supplied guide permits a stable compatible version; its recommended Boot 4.1.1 was confirmed against official requirements and successfully resolved from Maven Central. PostgreSQL 18.0 is deliberately the same exact image in Compose and Testcontainers. All container images have immutable digests. Temurin 21 JDK/JRE image digests capture their exact contents even though readable tags track the Java major. GitHub Actions use immutable commit SHAs.

The development workstation initially had Temurin 21.0.5+11 and no Docker/WSL. An isolated PostgreSQL 18.0 database was used to execute the same integration suite through the explicitly selected external-test mode. Default `clean verify` still requires Docker and starts Testcontainers; CI verifies that path. The optional Chromium test harness uses Playwright 1.56.1 with a lockfile and has no role in building or serving the UI.

References: [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [managed dependencies](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html), [Testcontainers PostgreSQL](https://java.testcontainers.org/modules/databases/postgres/), [PostgreSQL image](https://hub.docker.com/_/postgres).
