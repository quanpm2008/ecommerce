# identity-service — Improvements (should do)

Open findings from Codex reviews. Severity is Codex's; the note is our assessment. Findings marked *ours* were not raised by Codex.

## Step 0 tests

Open findings from the Codex review of the step 0 tests (2026-10-29, `gpt-5.6-terra`). Findings 9 (move `WebContractTests` to `@WebMvcTest`) and 8 (step 1) are done.

| # | Severity | Where | Problem | Fix | Note |
|---|---|---|---|---|---|
| 1 | High | `WebContractTests` paging tests | Lenient paging for `page=0` and negative pages is untested | Add `page=0` and `page=-1` cases: 200, `meta.page == 1`, first item `0` | Lenient paging is our own choice, not a spec rule; low priority but cheap |
| 2 | Medium | `GlobalExceptionHandler.codeFor` | 401, 403, 429, 503 mappings and other MVC 4xx (e.g. 415) are never exercised | Test endpoints throwing `ResponseStatusException` per status, plus a 415 request; assert status and code | Real coverage gap |
| 3 | Medium | `beanValidationFailureListsEachField` | Each detail's `message` is not asserted | Assert one detail per field with a non-empty `message` | |
| 4 | Medium | `malformedJsonIsAValidationError` | Only checks `details` is an array | Assert `details == []`, the default message, `requestId == null` | |
| 5 | Medium | `unexpectedExceptionDoesNotLeakItsMessage` | Expected message is read from `ErrorCode`, so a bad change there still passes | Assert the literal `"Unexpected error"` and that the body doesn't contain `hunter2` | |
| 6 | Medium | Request id tests | Only tested on an error response with one value; empty id, >128 chars, allowed-character edges and MDC cleanup untested | Add a success-response (`/test/item`) case, length/charset boundary cases, and a `RequestIdFilter` unit test that checks MDC inside and after the chain | MDC test is nice to have |
| 7 | Low | `pageDefaultsToFirstPageOfTwenty`, `pageSizeIsCappedAtOneHundred` | No status or content assertions | Add `isOk()` and data assertions (first item `0`; 12 items for the cap) | |
| 8 | Medium | `IdentityServiceApplicationTests.flywayManagesTheIdentitySchema` | Only proves the Flyway history table exists | Assert the expected migration version and an Identity table | **Done in step 1**: now asserts versions 1, 2 and the exact table list |

## Audit package (`audit/`, `admin_audit_log`)

Codex review of `audit/`, the `admin_audit_log` table in `V1__identity_schema.sql` and `AdminBootstrap` against `docs/database.docx` and `docs/api.docx` (2026-10-29, `gpt-5.6-terra`, read-only).

| # | Severity | Where | Problem | Fix | Note |
|---|---|---|---|---|---|
| A1 | High | Audit writers | Only the bootstrap writes audit rows; approve, reject, ban, unban, disable, enable and role revoke write none (DB §6, DB §8 "Mọi hành động của Admin ghi AdminAuditLog") | Write the row in the same transaction as each admin action | **Done in step 7** for approve, reject, ban, unban, disable and enable (`AuditTrail`); role grant/revoke rows remain for step 9 (checklist §9) |
| A2 | Medium | `admin_audit_log.target_type` | No DB allow-list; the Java enum limits it but SQL accepts any string, which could blur the USER/CUSTOMER/SELLER scope distinction (shared ids) | V3 migration: `CHECK (target_type IN ('USER', 'CUSTOMER', 'SELLER'))` | **Done in step 9** (V5 `ck_admin_audit_log_target_type`) |
| A3 | Medium | `AdminAuditLog.beforeAfter` | Free-form `Map<String, Object>`, so a future caller could store a token, reset URL or password ("không ghi token/mật khẩu", DB §6) | Typed factories per action (e.g. `AdminAuditLog.ban(...)`) that build the map from known fields only; no raw-map constructor in callers | **Done in step 7**: admin actions go through `AuditTrail` with typed `AuditState` snapshots; only the bootstrap still uses the raw constructor |
| A4 | Medium | `admin_audit_log.reason` | Nullable and unbounded, while reject and ban require `reason` 1–1000 chars (API §6.1) | Validate in the step 7 request DTOs; optionally a DB CHECK requiring `reason` for REJECT/BAN/DISABLE | Partly agree: request validation is the main fix, the DB check is optional. **Request validation done in step 7** (reject and ban `reason` 1–1000, unban ≤ 1000); DB check not added |
| A5 | Medium | `AdminBootstrap` | ADMIN grant doesn't bump `authzVersion` or write `user.roles_changed` (DB §5.8, API §7.2) | Bump on every role grant; write the event once the outbox exists (step 9) | **Bump done in step 2** (`RoleAssignments`, admin now at version 2); **`user.roles_changed` done in step 9** (written by `RoleAssignments` for every change, including the bootstrap grant) |
| A6 | Medium | `AuditAction` | `RETRY_REFUND` missing (API refund retry "Ghi AuditLog RETRY_REFUND") | — | Disagree: refund retry is a Payment admin action; DB §6 puts AdminAuditLog in "Identity và quản trị từng service", so Payment keeps its own |
| A7 | Low | `AdminAuditLogRepository.findByTargetTypeAndTargetIdOrderByHappenedAt` | Ordering not stable for equal timestamps | Add `auditId` as a tie-breaker | Suggestion, not a spec rule; cheap |
| A8 | High | `AuditAction`, `ck_admin_audit_log_action` | *Ours (Codex read the line but didn't report it).* The docs conflict on action names: DB §6 lists `APPROVE, REJECT, BAN, UNBAN, DISABLE, ENABLE, GRANT_ROLE, REVOKE_ROLE` (what the code uses); the API enum table lists `APPROVE_SELLER, REJECT_SELLER, BAN, UNBAN, DISABLE_USER, ENABLE_USER, GRANT_ROLE, REVOKE_ROLE` plus Catalog/Payment actions | Team decides which doc wins; if the API names, rename in a V3 migration (CHECK + enum) | Open decision. Only the Identity subset applies to this service either way |
| A9 | Low | `customers.ban_mode` | *Ours.* Redundant with `has_paid_order_before_ban` (true ⇔ PAYMENT_ONLY), so a buggy write could store them inconsistently | V3: drop `ban_mode` and derive `getBanMode()` in Java (or add a CHECK tying the two) | Not an audit-package finding, but came up in the same review; recommended: drop and derive |
