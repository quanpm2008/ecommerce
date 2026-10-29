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
  - `spring-security-crypto` (password hashing only; no Spring Security filter chain yet)
  - `lombok` (optional, compile-time only; run through the compiler's annotation processor and excluded from the jar)
  - test: `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`, `spring-boot-testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`
- **Config** (`application.properties`):
  - `spring.application.name=identity-service`, `server.port=8083`, Eureka at `http://localhost:8761/eureka/`
  - default datasource `jdbc:postgresql://localhost:5432/ecommerce` for running on the host (overridden by `SPRING_DATASOURCE_*` in Docker)
  - all tables live in the PostgreSQL schema **`identity`**: Flyway creates it and keeps `identity.flyway_schema_history` there; Hibernate uses it as the default schema, and `spring.datasource.hikari.schema=identity` sets the search path on every pooled connection so `JdbcTemplate` and native SQL resolve unqualified table names there too
  - `ddl-auto=validate` (schema changes only through Flyway), `open-in-view=false`
  - paging: 1-based `page`, default `size` 20, max 100
  - log lines include `[requestId]`
- **Migrations**: Flyway, SQL files in `src/main/resources/db/migration` (`V<n>__<description>.sql`). Never edit an applied migration; add a new one.
  - `V1__identity_schema.sql`: every Identity table from DB §3.1 and §6 — `users`, `customers`, `sellers`, `customer_addresses`, `roles`, `permissions`, `user_roles`, `role_permissions`, `auth_sessions`, `password_reset_tokens`, `admin_audit_log`, `outbox_events`. No `LoginIdentifier` or `AdminAccount` table.
  - Enums are `VARCHAR` + `CHECK` (mapped with `@Enumerated(STRING)`). Conditional fields are biconditional CHECKs: e.g. `disabled_reason` is set exactly when DISABLED, `rejection_reason` exactly when REJECTED, `reviewed_at`/`reviewed_by_user_id` exactly when not PENDING. Clear them when the state goes back.
  - The database also enforces: email stored trimmed + lowercase, phone numbers in `0xxxxxxxxx` form (`users`, `sellers.contact_phone`, `customer_addresses.recipient_phone`), the LOCAL/GOOGLE credential split, no self-review of a seller application.
  - `seller_name` (normalized: trimmed, whitespace collapsed, lowercase) and `contact_phone` are unique through partial indexes over PENDING/APPROVED sellers, so a REJECTED seller frees them.
  - `customers.ban_mode` + `has_paid_order_before_ban` hold the G-14 policy snapshot taken at ban time (API §10.4); required exactly when BANNED.
  - `customer_addresses.deleted_at` is the soft delete (G-13). The default address is derived, not stored.
  - `V2__seed_rbac.sql`: roles CUSTOMER/SELLER/ADMIN and the API §3.2.1 permission matrix (9/7/7). The three baseline permissions are in `permissions` but linked to no role.
- **Domain model** (entities validated by `ddl-auto=validate`): `user.User` (+ `UserRepository`), `customer.Customer` / `CustomerAddress`, `seller.Seller` (profiles use `@MapsId`, so their id *is* the user id), `rbac.Role` / `Permission` / `UserRole`, `audit.AdminAuditLog` (`before_after` is JSONB). Entities use Lombok `@Getter` + `@NoArgsConstructor(access = PROTECTED)` and a creation constructor; there are no setters, and state changes go through methods added with the features that need them. Never put `@Data`, `@Setter`, `@EqualsAndHashCode` or `@ToString` on an entity (they bypass invariants such as the `authzVersion` bump, break Hibernate identity, and `toString` would load lazy associations and print `passwordHash`). Services use `@RequiredArgsConstructor` and `@Slf4j`. `User.disable(reason)` already bumps `authzVersion`. `auth_sessions`, `password_reset_tokens` and `outbox_events` have no entity yet (steps 2, 3, 9).
- **Effective permissions** (`rbac.PermissionResolver`): baseline policy (`rbac.Permissions.BASELINE`) + the union of unrevoked roles, read from the database on every call; a DISABLED user has none. Profile status and ownership are not part of it; endpoints check them.
- **Password policy** (`user.PasswordPolicy`): G-07 (≥ 8 chars, upper, lower, digit) plus BCrypt's 72-byte limit. Hashes use Spring's delegating encoder (`{bcrypt}...`, bean in `common.security.PasswordConfig`).
- **Admin bootstrap** (`bootstrap.AdminBootstrap`): if `identity.bootstrap.admin.email` is set, startup creates that User (LOCAL, ACTIVE) with the ADMIN role and an audit row (`GRANT_ROLE`, actor null). Password from `identity.bootstrap.admin.password` must pass the policy or startup fails; `full-name` defaults to `Administrator`. If the email already belongs to a user, nothing happens: an existing account is never escalated. In Docker or on the host, use `IDENTITY_BOOTSTRAP_ADMIN_EMAIL` / `IDENTITY_BOOTSTRAP_ADMIN_PASSWORD`.
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
- **Tests**: `mvn test` starts a throwaway `postgres:16` with Testcontainers (`TestcontainersConfiguration`, import it with `@Import`); Docker must be running. `IdentityServiceApplicationTests` checks the context, the applied migration versions and the exact table list of the `identity` schema; `SchemaConstraintsTests` checks the DB rules above with plain SQL (one expected violation per test, since a failed statement aborts the PostgreSQL transaction); `rbac.RbacSeedTests` compares the seed with the spec matrix (`rbac.RbacMatrix`, written out independently); `rbac.PermissionResolverTests` and `bootstrap.AdminBootstrapTests` cover effective permissions and the bootstrap; `common.WebContractTests` is a `@WebMvcTest` slice (no database, no Docker) covering the envelope, paging, error mapping and request id through a test-only controller. Open improvements from Codex reviews (step 0 tests, audit package) are listed in [should.md](should.md).
- **Hello World**: `HelloController` serves `GET /hello`, which returns `Hello, World! from identity-service`.

## Not implemented yet

Tracked in [identity-service-checklist.md](identity-service-checklist.md). Sections 0 (foundation) and 1 (schema and seed) are done; sections 2–11 are open. Tick items there as they are verified.

## Notes / open issues

- Token lifetimes (15 min access / 7 days refresh, P-06) are not finalized in the spec.
- Paging is lenient: `page` ≤ 0 is treated as page 1 and `size` > 100 is capped at 100, instead of returning 400. Revisit if the FE needs strict validation.
- Errors raised outside Spring MVC (e.g. a future Spring Security filter) don't go through `GlobalExceptionHandler`; the security setup in step 2 must write the same envelope from its entry point and access-denied handler.
- `auth_sessions` stores one `refresh_token_hash` per session, as in DB §6. Detecting reuse of an already-rotated refresh token (step 2) needs the previous hashes, so step 2 will add a migration (e.g. a rotated-token table or a token-family column).
- The bootstrap leaves a new admin at `authz_version` 1: no token can exist yet for a brand-new user. Decide in step 3 whether registration (User + Customer + CUSTOMER) follows the same rule or always bumps on a role grant.
- Outbox has only `PENDING`/`PUBLISHED`; retry counters or a `FAILED` state can be added with the publisher in step 9 (no message broker in the stack yet).
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
| 2026-10-29 | Step 1 (schema and seed): `V1__identity_schema.sql`, `V2__seed_rbac.sql`, JPA entities and repositories, `PermissionResolver`, `PasswordPolicy`, admin bootstrap; Hikari connections use the `identity` schema; should.md finding 8 done (53 tests passing). Migrations applied to the dev database |
| 2026-10-29 | Added Lombok: entities use `@Getter` + protected `@NoArgsConstructor`, services `@RequiredArgsConstructor` / `@Slf4j` (53 tests passing) |
| 2026-10-29 | Codex review of the audit package against the specs; findings A1–A9 recorded in `should.md` (open decision: audit action names differ between DB §6 and the API enum) |
