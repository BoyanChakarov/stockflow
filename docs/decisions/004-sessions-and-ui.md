# ADR 004 — Same-origin sessions and Thymeleaf

Accepted. A single Spring Boot application serves the API and a Thymeleaf interface. Session authentication, BCrypt and Spring Security CSRF protection match internal employee access without a second frontend stack or bearer-token storage. Unsafe requests fetch the current session CSRF token. API access failures return JSON; unauthenticated pages redirect to login.

Demo account setup is an explicit profile with environment-provided initial passwords, no reset on restart, and synthetic data seeded through the same receipt service. Production excludes this initializer. Account administration is outside the MVP. A separate optional Playwright harness verifies the delivered browser UI; Node is not needed to run StockFlow.
