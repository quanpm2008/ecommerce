# identity-service — Test improvements (should do)

Open findings from the Codex review of the step 0 tests (2026-10-29, `gpt-5.6-terra`). Finding 9 (move `WebContractTests` to `@WebMvcTest`) is done. Severity is Codex's; the note is our assessment.

| # | Severity | Where | Problem | Fix | Note |
|---|---|---|---|---|---|
| 1 | High | `WebContractTests` paging tests | Lenient paging for `page=0` and negative pages is untested | Add `page=0` and `page=-1` cases: 200, `meta.page == 1`, first item `0` | Lenient paging is our own choice, not a spec rule; low priority but cheap |
| 2 | Medium | `GlobalExceptionHandler.codeFor` | 401, 403, 429, 503 mappings and other MVC 4xx (e.g. 415) are never exercised | Test endpoints throwing `ResponseStatusException` per status, plus a 415 request; assert status and code | Real coverage gap |
| 3 | Medium | `beanValidationFailureListsEachField` | Each detail's `message` is not asserted | Assert one detail per field with a non-empty `message` | |
| 4 | Medium | `malformedJsonIsAValidationError` | Only checks `details` is an array | Assert `details == []`, the default message, `requestId == null` | |
| 5 | Medium | `unexpectedExceptionDoesNotLeakItsMessage` | Expected message is read from `ErrorCode`, so a bad change there still passes | Assert the literal `"Unexpected error"` and that the body doesn't contain `hunter2` | |
| 6 | Medium | Request id tests | Only tested on an error response with one value; empty id, >128 chars, allowed-character edges and MDC cleanup untested | Add a success-response (`/test/item`) case, length/charset boundary cases, and a `RequestIdFilter` unit test that checks MDC inside and after the chain | MDC test is nice to have |
| 7 | Low | `pageDefaultsToFirstPageOfTwenty`, `pageSizeIsCappedAtOneHundred` | No status or content assertions | Add `isOk()` and data assertions (first item `0`; 12 items for the cap) | |
| 8 | Medium | `IdentityServiceApplicationTests.flywayManagesTheIdentitySchema` | Only proves the Flyway history table exists | Assert the expected migration version and an Identity table | Do in step 1, once `V1__init.sql` exists |
