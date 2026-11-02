# gateway-service — Verification checklist

Use this to decide whether the API Gateway is done. Tick an item only when an automated test or a manual check against the running gateway shows the stated behavior. References: `docs/api.docx` (API §), `docs/frd.docx` (UC-xx). Progress and open issues live in `gateway-service.md`.

The gateway's job (API §2.1): routing, JWT verification, removing identity headers sent by the client, rate limiting and CORS. It does **not** decide permissions: every service verifies the token again and checks current RBAC with Identity (API §2.3).

Paths are written in full (`/api/v1/...`, `/internal/v1/...`) because the gateway sees the full path.

## 0. Foundation

- [x] One web stack chosen and the other removed from `pom.xml` (`gateway-server-webmvc` servlet *or* reactive `webflux`); security and route config use the matching APIs — servlet (`gateway-server-webmvc`), the same as identity-service; webflux and webflux-test removed, `dependency:tree` has no webflux or reactor-netty
- [x] Temporary `HelloController` and the temporary `SecurityConfig` replaced (no `/hello` left) — `security.SecurityConfig` is stateless with no CSRF, form login, basic auth or logout; it lets everything through until JWT (§3)
- [x] `mvn test` passes with no manual env vars and no running Eureka or downstream services — `eureka.client.enabled=false` in tests; downstream stubs (e.g. WireMock) arrive with routing (§1)
- [ ] All settings come from properties with env overrides: Eureka URL, JWT issuer/audience/JWKS URL, CORS origins, rate limits, timeouts — *Eureka URL, actuator and (§1) timeouts done; each later step adds its own properties (§3 JWT, §6 CORS, §7 rate limits). Tick when those are in.*
- [x] No datasource needed: the app starts without `SPRING_DATASOURCE_*` — no JDBC on the classpath (`startsWithoutADatabase`); in compose the gateway has its own `depends_on` (Eureka only) and `environment` (Eureka URL only)
- [x] `GET /actuator/health` is public and reports UP; other actuator endpoints are not exposed — no details or components, `/actuator` index page off; `GatewayServiceApplicationTests` and a manual check in Docker
- [x] Port 8081 consistent across `application.properties`, Dockerfile `EXPOSE` and `compose.yaml`; the API §3.1 base URL `http://localhost:8080/api/v1` conflict resolved and written down (8080 is cart-service) — the gateway stays on 8081; the FE base URL is `http://localhost:8081/api/v1` (`DEVELOPMENT.md`)

## 1. Routing (API §2.2)

Routes resolve services through Eureka (`lb://<spring.application.name>`), never fixed host:port. Services serve the full public path, so the path is forwarded unchanged (no prefix stripping).

| Public path | Service |
|---|---|
| `/api/v1/identity/**` | identity-service |
| `/api/v1/categories/**`, `/api/v1/products/**`, `/api/v1/seller/products/**`, `/api/v1/admin/products/**`, `/api/v1/admin/categories/**`, `/api/v1/media/upload-urls` | product-service (Catalog) |
| `/api/v1/cart/**` | cart-service |
| `/api/v1/orders/**` | order-service |
| `/api/v1/payments/**` | payment-service |
| `/api/v1/finance/**` | finance-service |
| `/api/v1/notifications/**` | notification-service |

- [x] Every row above reaches the right service, checked with one request per prefix — `RoutingTests` (stub services through the simple discovery client); in Docker against identity-service through Eureka
- [x] `/api/v1/identity/media/upload-urls` goes to Identity; `/api/v1/media/upload-urls` goes to Catalog
- [x] There is no `/api/v1/catalog/**` route (API §10.5): it returns 404
- [x] `/api/v1/seller/**` and `/api/v1/admin/**` paths not in the table (e.g. `/api/v1/admin/foo`) return 404 and are not sent to any service
- [x] Unknown paths, and anything outside `/api/v1`, return 404 `NOT_FOUND` in the error envelope — `GatewayErrorController` replaces Boot's `/error`, so this also holds with `Accept: text/html`; static resource mappings are off
- [x] Method, query string, body and status code pass through unchanged (including 201, 202, 204 with no body, and 409/422 bodies) — raw query encoding kept; `Idempotency-Key`, `Accept-Language`, `Location` too
- [x] Downstream error bodies are returned exactly as the service sent them; the gateway does not rewrite them — 400/401/409/422/503 checked byte for byte
- [x] Service not registered in Eureka, connection refused, or timeout → 503 `SERVICE_UNAVAILABLE`; the timeout can be configured — `DownstreamFailureTests`; `GATEWAY_CONNECT_TIMEOUT` (2 s) and `GATEWAY_READ_TIMEOUT` (30 s, longer than the 15 s payment wait P-02)
- [x] With two instances of a service registered, requests are load-balanced (round robin, `requestsAreSpreadOverInstances`)
- [ ] An instance that goes down stops getting traffic — *deregistered (normal shutdown): yes, within the Eureka client's 30 s refresh, since the LoadBalancer cache is off. Crashed: it stays in Eureka until its lease expires (about 90 s) and its requests get 503 meanwhile; retrying on another instance is not built. Decide whether that is acceptable for the MVP.*
- [x] Not routed: identity's `/.well-known/jwks.json`, any service's `/actuator/**`, `/v3/api-docs/**` and `/swagger-ui/**`
- [x] Object storage presigned uploads (port 8333) do not go through the gateway (API §3.9): the browser sends them straight to storage — no route exists for it

## 2. Blocking `/internal/**` (API §2.3, §7.1)

- [ ] Every external request to `/internal/**` is rejected and never reaches a service. Any method, with or without a token or `X-Service-Token`. Status and code decided and documented (404 `NOT_FOUND` hides the path, 403 `FORBIDDEN` is explicit)
- [ ] `/api/v1/internal/**` and other paths that put `internal/v1` under a public prefix are rejected too, or are not routable at all
- [ ] Encoding and normalization tricks cannot get through: `/api/v1/identity/../../internal/v1/...`, `%2e%2e`, `%2F`, `//internal/v1`, `/INTERNAL/v1`, `;` path parameters, trailing `/`
- [ ] Client-sent `X-Service-Token` is removed on public routes, so the gateway is never used to pass a service credential *(not in the spec; defensive)*
- [ ] In the Docker setup, services' own ports being published to the host (8083 etc.) is a dev shortcut and recorded as such. In a deployment only the gateway is reachable from outside

## 3. JWT verification (API §2.3, §3.2)

Tokens are issued by identity-service: RS256, `iss` `ecommerce-identity`, `aud` `ecommerce-api`, public key at `http://identity-service:8083/.well-known/jwks.json`.

- [ ] The JWKS is fetched from identity-service and cached; the URL can be configured (in Docker this is a service URL, not localhost)
- [ ] The gateway starts and serves public routes while identity-service is still starting (keys are fetched lazily or retried, not required at boot)
- [ ] Only RS256 is accepted: `alg: none`, HS256 signed with the public key, and other algorithms → 401
- [ ] Bad signature, wrong `iss`, wrong `aud`, expired `exp`, or a missing `sub`/`sessionId` → 401
- [ ] Expired token → 401 `TOKEN_EXPIRED`; any other invalid token → 401 `UNAUTHENTICATED`, both in the error envelope with `requestId`
- [ ] Malformed `Authorization` header (not `Bearer <token>`, empty token, several Bearer values) → 401
- [ ] Small clock skew is allowed and the amount can be configured
- [ ] A valid token is forwarded unchanged as `Authorization: Bearer ...` so the service can verify it again
- [ ] Identity rotates its signing key (restart without `IDENTITY_TOKEN_SIGNING_KEY`): new tokens are accepted after the JWKS refreshes, without restarting the gateway
- [ ] Public endpoints work with **no** token: register, register-seller, login, refresh, password forgot/reset, regions, public sellers, public catalog reads, and guest cart if Cart allows it. How the gateway knows a path is public is decided and documented: a path allowlist at the gateway, or "verify only when a token is sent" and leave the decision to the service
- [ ] Decided and documented what happens to an invalid token sent to a public endpoint (reject with 401, or drop it and forward the request as anonymous)
- [ ] The gateway does not decide permissions or roles, does not read `roles` from the token, and does not cache authorization. Whether it also calls `POST /internal/v1/identity/auth/authorization-context` (the spec lists the Gateway as a consumer) is decided. If it does: it needs its own `X-Service-Token`, revoked session or stale `authzVersion` → 401, Identity down → 503 `SERVICE_UNAVAILABLE`, never allowed through

## 4. Headers (API §2.3, §3.10)

- [ ] `X-User-Id`, `X-Actor-Id`, `X-Actor-Type`, `X-Permissions` sent by the client are removed before forwarding. Checked with mixed case and with the header repeated, on public and protected routes
- [ ] The gateway does not add its own identity headers either; services take `sub` from the verified JWT
- [ ] Forwarded unchanged: `Authorization`, `Content-Type`, `Idempotency-Key`, `Accept-Language`, `If-Match` / version headers if used (API §3.8)
- [ ] `X-Forwarded-For` / `X-Forwarded-Proto` / `X-Forwarded-Host` are set by the gateway. A client-supplied `X-Forwarded-For` is not trusted as-is, so identity's per-IP limits (register, forgot-password) see the real client IP and cannot be dodged by faking the header
- [ ] Hop-by-hop headers (`Connection`, `Transfer-Encoding`, ...) are handled correctly; `Host` is right for the downstream

**`X-Request-Id`**
- [ ] Missing → the gateway generates one (e.g. UUID) and forwards it
- [ ] Sent by the client in a safe format → kept. Unsafe (does not match `[A-Za-z0-9._-]{1,128}`, the pattern identity-service accepts) → replaced with a generated one
- [ ] Returned in the response header for every request, including gateway errors, 404s and 429s
- [ ] In every gateway log line for the request (MDC), and equal to `error.requestId` in gateway errors
- [ ] Downstream services receive it and echo the same value (checked against identity-service)

## 5. Error envelope (API §3.3, §3.4, §4.1)

- [ ] Every error the gateway itself produces uses `{ "error": { code, message, details, requestId } }` with `Content-Type: application/json`; never the Spring Whitelabel page, a default `/error` JSON, or an empty 401/403 body
- [ ] Codes come only from API §4.1: `UNAUTHENTICATED`, `TOKEN_EXPIRED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404), `RATE_LIMITED` (429), `INTERNAL_ERROR` (500), `SERVICE_UNAVAILABLE` (503)
- [ ] Messages in English (team decision, same as identity-service); no stack traces, class names or downstream host names in the body
- [ ] Request body too large → rejected with a clear error (limit can be configured; status and code decided)

## 6. CORS (API §2.1)

- [ ] Allowed origins are exactly the three web apps (Customer, Seller, Admin), set by config per environment; no `*`
- [ ] A preflight `OPTIONS` from an allowed origin gets 200 with the right `Access-Control-Allow-*` headers, without a token, and is not forwarded to a service
- [ ] Allowed methods: GET, POST, PUT, PATCH, DELETE. Allowed request headers: `Authorization`, `Content-Type`, `Idempotency-Key`, `X-Request-Id`, `Accept-Language`
- [ ] `X-Request-Id` is in `Access-Control-Expose-Headers` so the browser can read it
- [ ] A disallowed origin gets no `Access-Control-Allow-Origin` header
- [ ] CORS headers are not duplicated: the gateway sets them and the services don't (or downstream values are removed)
- [ ] CORS headers are present on error responses too (401, 404, 429, 503), so the browser can show the error

## 7. Rate limiting (API §3.4 N-04)

N-04 gives no numbers yet; the values used are proposals and must be configurable.

- [ ] Per-client-IP limit on public routes; 429 `RATE_LIMITED` in the error envelope, with `Retry-After`
- [ ] Stricter limits on sensitive endpoints: `POST /api/v1/identity/auth/login`, `/register`, `/register-seller`, `/refresh`, `/password/forgot`, `/password/reset`
- [ ] Optional per-user limit (by `sub`) on authenticated routes
- [ ] The client IP is the real one (see `X-Forwarded-For` above); faking the header does not reset the counter
- [ ] Limits and windows set by properties; how counters work with more than one gateway instance is decided (in-memory is fine for the MVP, and written down)
- [ ] How it fits identity-service's own per-IP limits (register, forgot-password) is decided; requests under the gateway limit are not rejected without reason

## 8. Security hygiene

- [ ] `Authorization` header values, tokens, passwords and reset tokens never appear in gateway logs (request logging masks them)
- [ ] Access log: method, path, status, duration, `requestId` and client IP for every request; no request bodies
- [ ] No server/version headers that leak the stack (`Server`, `X-Powered-By`)
- [ ] Stateless: no HTTP session, no cookies set; CSRF disabled because the API uses Bearer tokens only

## 9. Docker and compose

- [ ] `./up.sh` builds and starts the gateway; it waits for discovery-service to be healthy and registers with Eureka
- [ ] In compose the JWKS URL and Eureka URL point to service names, not localhost
- [ ] The image runs as UID 10001 and the health check passes
- [ ] `DEVELOPMENT.md` documents the gateway URL, the base URL to give the FE, and how to run the gateway on the host against services in Docker

## 10. End-to-end through the gateway (API §10.5)

All of these go through `http://localhost:8081/api/v1` with the stack in Docker.

- [x] register → login → `GET /api/v1/identity/users/me` → refresh → logout; after logout the old access token is refused (by Identity, through the gateway) — manual check 2026-11-02 (201, 200, 200, 200, 204, then 401 `UNAUTHENTICATED`)
- [ ] A request with a forged `X-User-Id` for another user still acts as the token's `sub`
- [ ] `POST /internal/v1/identity/auth/authorization-context` from outside is blocked, even with a valid `X-Service-Token`
- [ ] A token issued before a role change gets 401 `TOKEN_STALE` from the service; the gateway passes it through unchanged
- [ ] Identity stopped: public identity routes → 503 `SERVICE_UNAVAILABLE`; protected routes on other services → 503 (from the service, not allowed through)
- [ ] One request's `X-Request-Id` can be found in the gateway log, the identity log and the error body
- [ ] The FE dev origin can call login and `users/me` from a browser (CORS) and read `X-Request-Id`
- [ ] Routes for services not built yet (Catalog, Cart, Order, Payment, Finance, Notification) are checked once each service has an endpoint; until then 503 is expected
