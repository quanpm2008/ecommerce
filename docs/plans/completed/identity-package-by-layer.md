# Execution Plan: Identity package by layer

Date: 2026-11-27

## Status

Completed

## Outcome

`identity-service` is organized **package by layer** (Controller → Service → Repository, with supporting
packages by technical role), like payment/order/finance. Behavior, HTTP contract, schema and
`openapi.json` stay exactly the same.

## Context

- Authority: SAD v1.1 `docs/requirements/software-architecture.md` (Package by Layer, line ~91; gap row
  "Identity hiện tổ chức nhiều package theo tính năng", ~1072) and `docs/engineering.md` REQ-ENG-007.
- Reference layouts: `payment-service`, `order-service` (`controller/ service/ repository/ entity/ dto/
  exception/ security/ config/ client/ messaging/`).
- User decision 2026-11-27: Identity only; `common/*` becomes top-level `exception/ security/ config/`;
  internal controllers go to `controller/internal/`, keeping their own security chain.

## Scope

In scope: moving identity-service main and test classes into layer packages, widening visibility
only where a move across packages needs it, updating docs that name identity packages/paths.

Out of scope: product/cart layout, behavior changes, renames, the gateway.

## Approach

Target packages under `com.example.identity_service`:

| Package | Contents |
|---|---|
| `controller` | public `@RestController`s, plus web-only helpers `RefreshCookies`, `StrictBody` |
| `controller.internal` | `InternalController`, `SellerSaleEvidenceController` (chain in `security.InternalSecurityConfig`) |
| `service` | `@Service`/`@Component` business logic, assemblers, policies, jobs (`BanReconciliation`), bootstrap, rate limiter, domain helpers (`PasswordPolicy`, `PhoneNumbers`, `AdminReasons`, ...) |
| `repository` | Spring Data repositories, `LikePattern` |
| `entity` | `@Entity`/`@Embeddable` and their enums |
| `dto` | request/response/view records, `ApiResponse`, `PageMeta` |
| `exception` | `ApiException`, `ErrorCode`, `ErrorResponse`, `GlobalExceptionHandler` |
| `security` | security chains, JWT issuing/decoding/validation, `Caller`, service-token filter/credentials, `Permissions` |
| `config` | `@ConfigurationProperties` records, `OpenApiConfig`/`ErrorCodes`, clock, media S3 config, `RequestIdFilter`, outbox scheduling |
| `client` | outbound adapters: obligations HTTP source, S3 `MediaStorage` |
| `messaging` | outbox writer/relay and event publishers |

Steps: scripted move (package lines + imports), compile, widen visibility where errors demand it,
move tests to the package of the class under test, run the full suite, regenerate OpenAPI, update docs.

## Risks And Recovery

- Package-private classes/members become public across packages: keep the widening minimal.
- Recovery: the work is one commit on `feature/api-gateway-identity`; revert it.

## Progress

- [x] Move main classes (scripted package/import rewrite), compile
- [x] Move tests to the package of the class under test, `mvn test`
- [x] `OpenApiDocsTests`: existing endpoints unchanged
- [x] Update docs (local CLAUDE.md, engineering REQ-ENG-007, identity.md / notifications.md code links, flowgraphs, progress file)

## Decisions

- 2026-11-27: Layout above (user-chosen for common/* and internal; rest follows payment/order).

## Validation

- Focused proof: `mvn -q compile`, `mvn test` in `identity-service` (Docker needed).
- Contract proof: `openapi.json` / `api.html` diff empty after `mvn test -Dtest=OpenApiDocsTests`.
- Repository-required checks: none beyond the service suite.

## Result

- 177 files moved, 25 declarations widened to `public` (entity constructors/domain methods, DTO factories,
  `PasswordResetToken`/`RotatedRefreshToken` + repositories, `AccessTokenIssuer`); no logic change.
- `mvn test`: 297 run, 295 pass. The 2 failures (`IdentityServiceApplicationTests` table list and Flyway
  versions) are pre-existing on the pre-refactor commit: main added `V12__seller_sale_evidence` without
  updating that test. Follow-up for the owner of V12.
- `openapi.json`/`api.html` regenerated; the only diff adds `POST /internal/v1/identity/sellers/sale-evidence`,
  merged from main without regenerated docs.
- SAD gap row (`software-architecture.md` ~1072) left as source text; REQ-ENG-007 records the new state.
