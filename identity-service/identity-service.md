# identity-service — Progress

The "Identity & Seller" service. It owns `User`, `Customer`, `Seller`, `CustomerAddress`, `Role`, `Permission`, `UserRole` and `RolePermission`. It issues JWTs and provides the authorization context that other services call (see `docs/api.docx` §3.2, §6.1 and `docs/database.docx` §3.1).

## Done

- **Scaffold**: Spring Boot 4.1.2, Java 21, Spring Cloud, package `com.example.identity_service`. `IdentityServiceApplication` is annotated with `@EnableDiscoveryClient`.
- **Dependencies** (`pom.xml`):
  - `spring-boot-starter-webmvc`
  - `spring-boot-starter-actuator`
  - `spring-cloud-starter-netflix-eureka-client`
  - `spring-cloud-starter-openfeign`
  - the `postgresql` driver
  - `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway` + `flyway-database-postgresql`, `spring-boot-starter-validation`
  - test: `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`, `spring-boot-testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`
- **Config** (`application.properties`):
  - `spring.application.name=identity-service`, `server.port=8083`, Eureka at `http://localhost:8761/eureka/`
  - default datasource `jdbc:postgresql://localhost:5432/ecommerce` for running on the host (overridden by `SPRING_DATASOURCE_*` in Docker)
  - all tables live in the PostgreSQL schema **`identity`**: Flyway creates it and keeps `identity.flyway_schema_history` there; Hibernate uses it as the default schema
  - `ddl-auto=validate` (schema changes only through Flyway), `open-in-view=false`
  - paging: 1-based `page`, default `size` 20, max 100
  - log lines include `[requestId]`
- **Migrations**: Flyway, SQL files in `src/main/resources/db/migration` (`V<n>__<description>.sql`). None yet.
- **Response envelope** (`common.web.ApiResponse`): controllers return `ApiResponse.of(dto)` → `{ "data": ... }` or `ApiResponse.page(page)` → `{ "data": [...], "meta": { page, size, totalItems, totalPages } }` (`PageMeta` converts to 1-based).
- **Errors** (`common.error`):
  - `ErrorCode` enum holds API §4.1 codes with their HTTP status and default English message (the spec asks for Vietnamese; English was chosen for this service). Only the generic codes exist so far; add feature codes when implementing them.
  - Throw `ApiException(code[, message, details])` from application code.
  - `GlobalExceptionHandler` renders every MVC exception as `{ "error": { code, message, details, requestId } }`. Bean validation → 400 `VALIDATION_ERROR` with `details: [{ field, issue, message }]` (`issue` is the constraint in UPPER_SNAKE, e.g. `NOT_BLANK`). Spring's own errors keep their status: 404 → `NOT_FOUND`, other 4xx (malformed JSON, 405, 415, ...) → `VALIDATION_ERROR`, 5xx → `INTERNAL_ERROR`. Unexpected exceptions are logged and return a generic message.
- **Request id** (`common.web.RequestIdFilter`): the gateway generates `X-Request-Id`; this service never invents one. An incoming value matching `[A-Za-z0-9._-]{1,128}` is echoed on the response and put in the log MDC and `error.requestId`. A missing or unsafe value is ignored: no response header, no id in logs, `error.requestId: null`.
- **Docker**:
  - Two-stage Dockerfile: Maven build, then `eclipse-temurin:21-jre`, running as UID 10001 and exposing 8083.
  - `.dockerignore`.
- **Compose**:
  - Registered via the `x-eureka-client` anchor, published on port 8083.
  - Waits for `discovery-service` and `database` to be healthy.
  - Gets `SPRING_DATASOURCE_URL=jdbc:postgresql://database:5432/ecommerce` (user and password `ecommerce`). This dev database is shared by all services.
- **Tests**: `mvn test` starts a throwaway `postgres:16` with Testcontainers (`TestcontainersConfiguration`, import it with `@Import`); Docker must be running. `IdentityServiceApplicationTests` checks the context and that Flyway manages the `identity` schema; `common.WebContractTests` is a `@WebMvcTest` slice (no database, no Docker) covering the envelope, paging, error mapping and request id through a test-only controller. Open test improvements are listed in [should.md](should.md).
- **Hello World**: `HelloController` serves `GET /hello`, which returns `Hello, World! from identity-service`.

## Not implemented yet

Tracked in [identity-service-checklist.md](identity-service-checklist.md). Section 0 (foundation) is done; sections 1–11 are open. Tick items there as they are verified.

## Notes / open issues

- Token lifetimes (15 min access / 7 days refresh, P-06) are not finalized in the spec.
- Paging is lenient: `page` ≤ 0 is treated as page 1 and `size` > 100 is capped at 100, instead of returning 400. Revisit if the FE needs strict validation.
- Errors raised outside Spring MVC (e.g. a future Spring Security filter) don't go through `GlobalExceptionHandler`; the security setup in step 2 must write the same envelope from its entry point and access-denied handler.
- On this machine (rootless Docker), Testcontainers once failed with "bind: address already in use" on host port 32768; rerunning worked.

## Log

| Date | Change |
|---|---|
| 2026-10-28 | Recorded the initial state: scaffold, Docker image and compose entry with datasource |
| 2026-10-28 | Added the `/hello` endpoint; made `mvnw` executable in git |
| 2026-10-29 | Added `identity-service-checklist.md` from the API and database specs |
| 2026-10-29 | Step 0 (foundation): JPA + Flyway on the `identity` schema, validation, response envelope, error handling, `X-Request-Id`, paging config, Testcontainers tests (14 passing) |
| 2026-10-29 | Removed the Maven wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`, `.gitattributes`); build and test with a locally installed `mvn` |
| 2026-10-29 | Error messages switched from Vietnamese to English |
| 2026-10-29 | `RequestIdFilter` no longer generates an id; it only uses the one forwarded by the gateway |
| 2026-10-29 | Codex test review: `WebContractTests` moved to `@WebMvcTest` (finding 9); remaining findings recorded in `should.md` |
