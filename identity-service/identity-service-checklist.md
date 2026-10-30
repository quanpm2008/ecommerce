# identity-service — Verification checklist

Use this to decide whether the Identity implementation is done. Tick an item only when an automated test or a manual check against the running service shows the stated behavior. References: `docs/api.docx` (API §), `docs/database.docx` (DB §), `docs/frd.docx` (UC-xx).

Endpoint paths are shown as the public path after `/api/v1` (e.g. `/identity/auth/login`) and the internal path after `/internal/v1`.

## 0. Foundation

- [x] `spring-boot-starter-data-jpa` enabled; schema created by versioned migrations (not `ddl-auto`)
- [x] `mvn test` passes without manual env vars (Testcontainers PostgreSQL 16; requires Docker)
- [x] Every JSON response uses the envelope: `{ "data": ... }`, lists add `meta { page, size, totalItems, totalPages }`, errors are `{ "error": { code, message, details, requestId } }` (API §3.3)
- [x] `X-Request-Id` from the gateway is echoed in the response, logged, and equals `error.requestId`; the service never generates one (missing or unsafe → `requestId: null`)
- [x] Validation errors return 400 `VALIDATION_ERROR` with per-field `details`
- [x] Paging defaults `page=1`, `size=20` on list endpoints

## 1. Schema and seed data (DB §3.1, §6)

- [x] `users`: `email` unique and stored trimmed + lowercase; `phone_number` unique when not null; `google_subject` unique when not null
- [x] CHECK on `users.auth_method`: LOCAL requires `password_hash` and null `google_subject`; GOOGLE the reverse
- [x] `users.status` ACTIVE/DISABLED; `disabled_reason` required when DISABLED; `authz_version` defaults to 1
- [x] `customers.customer_id` and `sellers.seller_id` are PK **and** FK → `users.user_id` (`ON DELETE RESTRICT`)
- [x] `ban_reason` required when BANNED; `rejection_reason` required when REJECTED
- [x] Partial unique indexes on normalized `seller_name` and `contact_phone` for PENDING/APPROVED sellers only (a REJECTED seller frees them)
- [x] `customer_addresses` FK → `customers`; supports soft delete
- [x] `roles`, `permissions`, `user_roles` (PK `user_id, role_id`, `revoked_at`), `role_permissions` (PK `role_id, permission_id`)
- [x] Seed contains exactly the roles CUSTOMER, SELLER, ADMIN and the permission matrix from API §3.2.1
- [x] Baseline policy (`account:read:own`, `account:update:own`, `seller:application:own`, create own Customer) applies to every ACTIVE user without a role
- [x] Support tables exist: `auth_sessions` (`refresh_token_hash` unique), `password_reset_tokens` (`token_hash` unique, `used_at`), `admin_audit_log`, `outbox_events`
- [x] Admin user can be bootstrapped (LOCAL + ADMIN role) without any public endpoint
- [x] No `LoginIdentifier` or `AdminAccount` table

## 2. Tokens and sessions (API §2.3, §3.2)

- [x] Access token claims: `sub` (= `user_id`), `iss`, `aud`, `iat`, `exp`, `jti`, `sessionId`, `authzVersion`; no email, phone or credentials
- [x] Tokens are signed and verifiable by the gateway and other services (public key / JWKS endpoint or shared config)
- [x] Access and refresh lifetimes are configurable (proposed 15 min / 7 days, P-06)
- [x] Only the refresh token **hash** is stored
- [x] Refresh rotates the token; the old one stops working
- [x] Reusing an already-rotated refresh token revokes the whole token chain
- [x] Refresh reloads roles and permissions from the database, never copies them from the old token
- [x] A token whose `authzVersion` is older than the user's current version is rejected with 401 `TOKEN_STALE`
- [x] A token whose session is revoked is rejected
- [ ] `authz_version` increments in the same transaction as every role or access-status change *(done for role grant/revoke via `RoleAssignments` and User disable/enable; Customer/Seller ban, unban and approval come in step 7 and must bump too)*

## 3. Auth endpoints (API §6.1, UC-A01–A05, A07)

**`POST /identity/auth/register`**
- [x] Creates User LOCAL + Customer + UserRole CUSTOMER in one transaction; returns 201 `UserSelfProfile` and no tokens
- [x] Email normalized to lowercase; phone normalized to `0xxxxxxxxx`; `confirmPassword` must match
- [x] 409 `EMAIL_ALREADY_USED` / `PHONE_ALREADY_USED` with the field in `details`; 400 `PASSWORD_POLICY_VIOLATION` (G-07)
- [x] Rate limited per IP

**`POST /identity/auth/register-seller`**
- [x] Creates User LOCAL + Seller PENDING (`seller_id = user_id`) in one transaction; no SELLER role, no wallet
- [x] Returns 201 `UserSelfProfile` with `sellerProfile.approvalStatus=PENDING`, `roles=[]`
- [x] 409 `EMAIL_ALREADY_USED`, `PHONE_ALREADY_USED`, `SELLER_NAME_ALREADY_USED`
- [x] Publishes `seller.registered` *(written to the outbox in the same transaction; delivery is step 9)*

**`POST /identity/auth/login`**
- [x] Single login for customer, seller and admin; request has no role or actor type
- [x] Returns `AuthTokens` with `UserSelfProfile` including both profiles when present
- [x] 401 `INVALID_CREDENTIALS`; 409 `WRONG_AUTH_METHOD` for Google accounts; 403 `ACCOUNT_DISABLED` for DISABLED users
- [x] PENDING / REJECTED / BANNED profiles still log in when the User is ACTIVE

**`POST /identity/auth/refresh`** and **`POST /identity/auth/logout`**
- [x] Refresh: 401 `INVALID_REFRESH_TOKEN` for disabled user, revoked session or unknown token
- [x] Logout revokes only the current session; expired or already-logged-out token still returns 204
- [x] Cannot log out someone else's session with your own access token

**`POST /identity/auth/google`** (Should; deferred, needs a Google client id)
- [ ] Verifies the Google ID token (issuer, audience, email verified)
- [ ] New subject + unused email → creates User GOOGLE + Customer + CUSTOMER, `isNewAccount=true`
- [ ] Email already registered with a password → 409 `WRONG_AUTH_METHOD`, accounts never merged
- [ ] 401 `GOOGLE_AUTH_FAILED` on invalid token

**Password**
- [x] `POST /identity/auth/password/forgot` returns the same 202 response whether the email exists, is Google, or is a pending seller (G-05); rate limited (429 `RATE_LIMITED`)
- [x] Reset token stored as hash, expires (P-05), link uses a configured trusted origin (never a client-supplied URL)
- [x] Publishes `password.reset_requested`; the reset URL is never logged *(outbox; delivery is step 9)*
- [x] `POST /identity/auth/password/reset` is single use, updates the LOCAL password, revokes all sessions; 400 `RESET_TOKEN_INVALID`
- [x] `PUT /identity/users/me/password` (LOCAL only) revokes all sessions including the current one; 400 `PASSWORD_INCORRECT`, `PASSWORD_SAME_AS_OLD`; Google account → 403

## 4. Self-service (UC-A02, A06)

- [ ] `GET /identity/users/me` returns `UserSelfProfile` with roles, permissions, `authzVersion` and both optional profiles
- [ ] `PATCH /identity/users/me` accepts only `fullName`, `phoneNumber`; any other field → 400 `VALIDATION_ERROR`; duplicate phone → 409
- [ ] `POST /identity/users/me/customer-profile`: 201 creates Customer + CUSTOMER and bumps `authzVersion`; 200 if it already exists; BANNED Customer is returned unchanged; CUSTOMER revoked by an admin → 403
- [ ] `POST /identity/users/me/seller-application`: 201 first time, 200 on resubmission after REJECTED, same `seller_id`; PENDING/APPROVED → 409 `INVALID_STATE_TRANSITION`; body never accepts userId/email/password/role/status
- [ ] `GET /identity/users/me/seller-application` returns own `SellerSelfProfile` or null without needing SELLER
- [ ] `PATCH /identity/sellers/me` updates description, `logoObjectKey`, `contactPhone`; cannot change email or seller name
- [ ] `POST /identity/media/upload-urls` only purpose `SELLER_LOGO`, only for APPROVED/ACTIVE seller; 422 `MEDIA_INVALID`

**Addresses** (`/identity/customers/me/addresses`)
- [ ] List returns only `customer_id = sub`, ordered: default first, then `lastUsedAt` desc, `createdAt` asc; `meta.maxAddresses` present
- [ ] Create validates the ward belongs to the province; 422 `ADDRESS_LIMIT_EXCEEDED` (P-09), 422 `INVALID_REGION`
- [ ] Update / delete of another customer's address → 404
- [ ] Deleting the last address is allowed; deleting an address referenced by an order is soft (G-13)

## 5. Regions (API §6.1 "Nguồn dữ liệu địa giới")

- [ ] `GET /identity/regions/provinces` and `/provinces/{code}/wards` proxy an external provider configured on the server (`GEO_API_BASE_URL`, timeout, dataset version)
- [ ] Responses include `meta { sourceVersion, fetchedAt }`; results cached (24 h proposed)
- [ ] Not configured, or timeout with no valid cache → 503 `REGION_SOURCE_UNAVAILABLE`; never falls back to a static list
- [ ] 422 `INVALID_REGION` only when the provider confirms the code is invalid

## 6. Public seller lookup (UC-B02)

- [ ] `GET /identity/sellers` returns only sellers with User ACTIVE, Seller APPROVED + ACTIVE, and the SELLER role active (G-06); case-insensitive search on name
- [ ] `GET /identity/sellers/{sellerId}` returns only `SellerPublic` (no email, phone, ban or review reasons); invalid seller → 404

## 7. Admin (UC-E01, E03, E06)

- [ ] `GET /identity/admin/customers`, `/customers/{id}`, `/sellers`, `/sellers/{id}` require `operations:read:any`; filters and sort as specified; never return password hash, Google subject or tokens
- [ ] Approve: PENDING only → APPROVED + SELLER role + `authzVersion` bump + audit + `seller.approved` event in one transaction
- [ ] Approve: 409 `ALREADY_PROCESSED` if not PENDING; 403 `SELF_APPROVAL_FORBIDDEN` when admin = seller
- [ ] Reject: PENDING only → REJECTED with reason, `authzVersion` bump, audit, `seller.rejected`; User and Customer kept; name and phone freed
- [ ] Ban requires `scope` USER / CUSTOMER / SELLER (missing or invalid → 400)
- [ ] Ban USER → DISABLED, all sessions revoked, `user.disabled`
- [ ] Ban CUSTOMER → BANNED per G-14, calling Order `has-paid-order`; result stored with the ban; Order unavailable → 503 and nothing changed; `customer.banned`
- [ ] Ban SELLER → BANNED, selling and withdrawal blocked, role kept, `seller.banned`
- [ ] Banning a user with ADMIN is refused; banning a missing profile → 404
- [ ] Same ban repeated → 200 with no new event or audit; different reason while already banned → 409 `INVALID_STATE_TRANSITION`
- [ ] Unban restores only that scope; never grants SELLER to an unapproved seller; already ACTIVE → 200 with no event

## 8. Internal API (API §7.1) — service credentials only

- [ ] Every internal endpoint rejects calls without a valid service credential
- [ ] `POST /internal/v1/identity/auth/authorization-context` returns `{ userId, userStatus, authzVersion, roles, permissions, customerProfile, sellerProfile, sessionActive }` and checks `sessionId` and token version
- [ ] `GET /identity/sellers/status?ids=` returns status and a `sellable` flag with no PII
- [ ] `GET /identity/customers/{id}/checkout-eligibility` returns `{ userStatus, customerStatus, paymentBlocked, hasPhone, addressCount, canCheckout, authzVersion }`
- [ ] `GET /identity/customers/{id}/addresses/{addressId}` returns a snapshot; 404 if the address belongs to another customer
- [ ] `POST /identity/customers/{id}/addresses/{addressId}/touch` updates `lastUsedAt`; repeating with the same `checkoutSessionId` has no extra effect
- [ ] `GET /identity/users/{userId}/contact` returns `{ userId, email, fullName }` only to allowlisted services (Notification)

## 9. Events and audit (API §7.2, DB §6)

- [ ] Events are written to `outbox_events` in the same transaction as the change and published afterwards
- [ ] Each event has the shared envelope (event id, actor) and the documented payload:
  - [ ] `seller.registered` / `seller.approved` / `seller.rejected`
  - [ ] `seller.banned` / `seller.unbanned`
  - [ ] `customer.banned` / `customer.unbanned`
  - [ ] `user.disabled` / `user.enabled`
  - [ ] `user.roles_changed`
  - [ ] `password.reset_requested`
- [ ] `admin_audit_log` gets a row for every approve, reject, ban, unban, disable, enable, role grant and role revoke, with actor, target, reason, before/after and request id; never tokens or passwords

## 10. Cross-cutting RBAC rules (DB §5.8, API §2.3)

- [ ] Endpoints check specific permissions, never just a role string
- [ ] Owner IDs always come from `sub`, never from the body, query or headers
- [ ] Role without profile → rejected; profile with a revoked role → rejected
- [ ] User with CUSTOMER + SELLER logs in once and can use both sets of APIs
- [ ] User with only ADMIN or only SELLER cannot use customer APIs
- [ ] ADMIN does not automatically have customer or seller permissions
- [ ] A client cannot grant itself ADMIN, change its own status, or approve its own shop
- [ ] Tokens issued before a revoke, ban, disable or permission change no longer grant the old permissions
- [ ] No permission caching: Identity unreachable → callers get 503 `SERVICE_UNAVAILABLE`, never allowed through
- [ ] Credentials, tokens and reset URLs never appear in logs or responses

## 11. Integration with the rest of the system

- [ ] Gateway routes `/api/v1/identity/**` to identity-service through Eureka
- [ ] Gateway blocks external requests to `/internal/**`
- [ ] Gateway verifies tokens issued by identity-service and removes client-sent `X-User-Id`, `X-Actor-Id`, `X-Actor-Type`, `X-Permissions`
- [ ] Finance creates exactly one wallet per `seller.approved`, even if the event is delivered twice
- [ ] Notification can send the reset-password and seller-lifecycle emails using `users/{id}/contact`
- [ ] Full flow works through the gateway in Docker: register → login → `GET /identity/users/me` → refresh → logout
