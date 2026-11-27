# Execution Plan: Identity session on User (GAP-IDN-005)

Date: 2026-11-27

## Status

Completed

## Outcome

The identity schema matches DB v5.1 §2/§6 and decision 0003: one refresh session per User, stored as
`users.refresh_token_hash` / `refresh_token_expires_at` / `session_id`; `auth_sessions` is gone;
`rotated_refresh_tokens` references `user_id` + `session_id`.

## Context

- Authority: [decision 0003](../../decisions/0003-mot-phien-refresh-tren-user.md), DB §3.1.1 and §6
  (`docs/requirements/database.md`), REQ-IDN-009 and GAP-IDN-005 (`docs/requirements/identity.md`), API §3.2.
- Table audit 2026-11-27: of the 20 identity tables, 13 are DB §2/§6 entities/technical tables, 6 are backed
  by other requirements (`region_*` P-26, `seller_sale_evidence` API §7, `address_touches` API §7.1 touch
  idempotency, `seller_reapplication_tokens` API #30/31), and `auth_sessions` is the one the docs remove.

## Scope

In scope: V13 migration (cutover: every old session revoked, users log in again), User session fields and
methods, SessionService/validator/authorization-context on User, tests, docs.

Out of scope: the other 19 tables, TTL values (P-06 open), API contract changes.

## Approach

1. V13: add the three User columns (all-or-none CHECK, both UNIQUE), recreate `rotated_refresh_tokens`
   (`token_hash` TEXT PK, `user_id` FK users, `session_id`, `rotated_at`), drop `auth_sessions`.
2. Login locks the User row, sets a new `session_id` + hash + expiry, deletes rotated marks of older sessions.
3. Refresh locks the User by current hash; rotates hash, keeps `session_id`, expiry = now + TTL. A rotated
   token of the current `session_id` clears the session (committed) then 401; any other unknown → 401.
4. Logout: Bearer user + session must equal `users.session_id`, and the cookie must be the current hash or a
   rotated mark of that session.
5. Validator and authorization-context: session active ⇔ `users.session_id = jwt.sessionId`.

## Risks And Recovery

- Everyone is logged out once by V13 (accepted by decision 0003).
- Recovery: revert the commit before V13 runs anywhere shared; after it runs, a forward migration.

## Progress

- [x] V13 + entities
- [x] SessionService, validator, authorization-context
- [x] Tests green (`mvn test`), OpenAPI unchanged
- [x] Docs (identity.md GAP-IDN-005, identity-service.md, checklist, session flowgraph)

## Decisions

- 2026-11-27: only `auth_sessions` is removed; the 6 tables backed by other requirements stay.

## Validation

- `mvn test` in identity-service; `OpenApiDocsTests` leaves `openapi.json` unchanged.

## Result

- V13 applied in Testcontainers; `IdentityServiceApplicationTests` expects 19 tables and versions 1–13.
- `mvn test`: 312 run, 0 failures. New/changed proof: `AuthEndpointsTests.aNewLoginReplacesThePreviousSession`,
  `aFailedLoginKeepsTheCurrentSession`, `logoutEndsTheSession`; `SessionServiceTests` (expiry move, replaced
  session cannot end the new one, logout with a rotated token); `PasswordEndpointsTests.changePasswordEndsTheCurrentSession`.
- `openapi.json`/`api.html` unchanged.
- Not done: concurrent-login/refresh race tests (relies on the row lock); SAD/system-architecture source text
  still says "code vẫn dùng AuthSession" (source docs left unchanged); `identity_service_dependency_graph.md` is stale
  (note added). V13 must run on Supabase/dev DBs: everyone signs in again.
