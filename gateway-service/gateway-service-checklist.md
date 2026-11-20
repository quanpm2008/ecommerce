# gateway-service — Verification checklist

Use this to decide whether the API Gateway is done. Tick an item only when an automated test or a manual check against the running gateway shows the stated behavior. References: API v1.1 `docs/requirements/api_specs.md` (API §), FRD v5.5 `docs/requirements/FRD.md` (UC-xx), SAD v1.1 `docs/requirements/software-architecture.md` (SAD §), decisions in `docs/decisions/`. Progress and open issues live in `gateway-service.md`.

The gateway's job (API §2.1): routing, JWT verification, removing identity headers sent by the client, rate limiting and CORS. It does **not** decide permissions: every service verifies the token again and checks current RBAC with Identity (API §2.3).

Paths are written in full (`/api/v1/...`, `/internal/v1/...`) because the gateway sees the full path.

## 0. Foundation

- [x] One web stack, the one the accepted baseline names: Spring Cloud Gateway **WebFlux** (SAD ADR-02, `docs/engineering.md` REQ-ENG-002); security and route config use the reactive APIs — `spring-cloud-starter-gateway-server-webflux` on Netty since 2026-11-20 (it was servlet/WebMVC before); `dependency:tree` has no `spring-webmvc` or Tomcat, `runsOnTheReactiveStackOnly`
- [x] Temporary `HelloController` and the temporary `SecurityConfig` replaced (no `/hello` left) — `security.SecurityConfig` is stateless with no CSRF, form login, basic auth or logout; it lets everything through until JWT (§3)
- [x] `mvn test` passes with no manual env vars and no running Eureka or downstream services — `eureka.client.enabled=false` in tests; downstream services are JDK `HttpServer` stubs (`support.StubService`)
- [x] All settings come from properties with env overrides: Eureka URL, JWT issuer/audience/JWKS URL, CORS origins, rate limits, timeouts — `GATEWAY_*` variables in `application.properties` (timeouts, `GATEWAY_JWT_*`, `GATEWAY_CORS_*`, `GATEWAY_MAX_BODY_SIZE`, `GATEWAY_RATE_LIMIT_*`); defaults checked in `edgeDefaultsAreTheDecidedOnes`
- [x] No datasource needed: the app starts without `SPRING_DATASOURCE_*` — no JDBC on the classpath (`startsWithoutADatabase`); in compose the gateway has its own `depends_on` (Eureka only) and `environment` (Eureka URL only)
- [x] `GET /actuator/health` is public and reports UP; other actuator endpoints are not exposed — no details or components, `/actuator` index page off; `GatewayServiceApplicationTests` and a manual check in Docker
- [x] Port 8081 consistent across `application.properties`, Dockerfile `EXPOSE` and `compose.yaml`; the API §3.1 base URL `http://localhost:8080/api/v1` conflict resolved and written down (8080 is cart-service) — the gateway stays on 8081; the FE base URL is `http://localhost:8081/api/v1` (`DEVELOPMENT.md`)

## 1. Routing (API §2.2)

Routes resolve services through Eureka (`lb://<spring.application.name>`), never fixed host:port. Services serve the full public path, so the path is forwarded unchanged, except product-service, which serves its paths without `/api/v1` (`/products`, ...): its route drops the base path and keeps the query string as sent (team decision 2026-11-20).

| Public path | Service |
|---|---|
| `/api/v1/identity/**` | identity-service |
| `/api/v1/categories/**`, `/api/v1/products/**`, `/api/v1/seller/products/**`, `/api/v1/admin/products/**`, `/api/v1/admin/categories/**`, `/api/v1/media/upload-urls` | product-service (Product; formerly Catalog), sent without `/api/v1` |
| `/api/v1/cart/**` | cart-service |
| `/api/v1/orders/**` | order-service |
| `/api/v1/payments/**` | payment-service |
| `/api/v1/finance/**` | finance-service |
| `/api/v1/notifications/**` | notification-service |

- [x] Every row above reaches the right service, checked with one request per prefix — `RoutingTests` (stub services through the simple discovery client, including the forwarded path); in Docker against identity-service through Eureka
- [x] `/api/v1/identity/media/upload-urls` goes to Identity; `/api/v1/media/upload-urls` goes to Product (as `/media/upload-urls`)
- [x] There is no `/api/v1/catalog/**` route (Product's public paths have no service prefix, API §2.2): it returns 404
- [x] `/api/v1/seller/**` and `/api/v1/admin/**` paths not in the table (e.g. `/api/v1/admin/foo`) return 404 and are not sent to any service
- [x] Unknown paths, and anything outside `/api/v1`, return 404 `NOT_FOUND` in the error envelope — `error.GatewayErrorHandler` runs ahead of Boot's error handler, so this also holds with `Accept: text/html`; static resource mappings are off
- [x] Method, query string, body and status code pass through unchanged (including 201, 202, 204 with no body, and 409/422 bodies) — raw query encoding kept; `Idempotency-Key`, `Accept-Language`, `Location` too
- [x] Downstream error bodies are returned exactly as the service sent them; the gateway does not rewrite them — 400/401/409/422/503 checked byte for byte
- [x] Service not registered in Eureka, connection refused, or timeout → 503 `SERVICE_UNAVAILABLE`; the timeout can be configured — `DownstreamFailureTests`; `GATEWAY_CONNECT_TIMEOUT` (2 s) and `GATEWAY_READ_TIMEOUT` (30 s, longer than the 15 s payment wait P-02)
- [x] With two instances of a service registered, requests are load-balanced (round robin, `requestsAreSpreadOverInstances`)
- [ ] An instance that goes down stops getting traffic — *deregistered (normal shutdown): yes, within the Eureka client's 30 s refresh, since the LoadBalancer cache is off. Crashed: it stays in Eureka until its lease expires (about 90 s) and its requests get 503 meanwhile; retrying on another instance is not built. Decide whether that is acceptable for the MVP.*
- [x] Not routed: identity's `/.well-known/jwks.json`, any service's `/actuator/**`, `/v3/api-docs/**` and `/swagger-ui/**`
- [x] Object storage presigned uploads (port 8333) do not go through the gateway (API §3.9): the browser sends them straight to storage — no route exists for it

## 2. Blocking `/internal/**` (API §2.3, §7.1)

- [x] Every external request to `/internal/**` (API v1.1 internal prefixes: `/internal/v1/identity/**`, `/products/**`, `/orders/**`, `/inventory/**`, `/payments/**`, `/finance/**`, `/notifications/**`) is rejected and never reaches a service. Any method, with or without a token or `X-Service-Token`. Status and code decided and documented (404 `NOT_FOUND` hides the path, 403 `FORBIDDEN` is explicit) — **404 `NOT_FOUND`**, the same as an unknown path; `security.InternalPathFilter` is the first filter of the security chain (before any token check), `InternalPathTests`, manual check in Docker
- [x] `/api/v1/internal/**` and other paths that put `internal/v1` under a public prefix are rejected too, or are not routable at all — any path segment `internal` (any case, after percent-decoding) is blocked; no public API path has one
- [x] Encoding and normalization tricks cannot get through: `/api/v1/identity/../../internal/v1/...`, `%2e%2e`, `%2F`, `//internal/v1`, `/INTERNAL/v1`, `;` path parameters, trailing `/` — 400 `VALIDATION_ERROR` (Spring Security's `StrictServerWebExchangeFirewall`, answered in the envelope) or 404; 16 variants tested, none reaches a service
- [x] Client-sent `X-Service-Token` is removed on public routes, so the gateway is never used to pass a service credential *(not in the spec; defensive)* — `RoutesConfig` (`removeRequestHeader`), every casing and repeat
- [x] In the Docker setup, services' own ports being published to the host (8083 etc.) is a dev shortcut and recorded as such. In a deployment only the gateway is reachable from outside — `DEVELOPMENT.md` and `gateway-service.md`

## 3. JWT verification (API §2.3, §3.2)

Tokens are issued by identity-service: RS256, `iss` `ecommerce-identity`, `aud` `ecommerce-api`, public key at `http://identity-service:8083/.well-known/jwks.json`.

- [x] The JWKS is fetched from identity-service and cached; the URL can be configured (in Docker this is a service URL, not localhost) — `GATEWAY_JWT_JWK_SET_URI`, compose sets `http://identity-service:8083/.well-known/jwks.json`; fetch timeout `GATEWAY_JWT_JWKS_TIMEOUT` (2 s)
- [x] The gateway starts and serves public routes while identity-service is still starting (keys are fetched lazily or retried, not required at boot) — `JwksUnavailableTests`; a token that can't be checked then is 503 `SERVICE_UNAVAILABLE`, never let through
- [x] Only RS256 is accepted: `alg: none`, HS256 signed with the public key, and other algorithms → 401 — also RS512 and PS256 with the right key
- [x] Bad signature, wrong `iss`, wrong `aud`, expired `exp`, or a missing `sub`/`sessionId` → 401 — 19 bad tokens in `JwtTests` (also missing `exp`, future `nbf`, blank or non-string `sub`/`sessionId`, tampered payload); in Docker against identity-service's real tokens
- [x] Expired token → 401 `TOKEN_EXPIRED`; any other invalid token → 401 `UNAUTHENTICATED`, both in the error envelope with `requestId` — `TOKEN_EXPIRED` only when expiry is the only problem; `WWW-Authenticate: Bearer` (no token) or `Bearer error="invalid_token"`
- [x] Malformed `Authorization` header (not `Bearer <token>`, empty token, several Bearer values) → 401 — `StrictBearerTokenConverter`; also `Basic ...` and two `Authorization` headers, on public endpoints too; a token in the query string is not accepted
- [x] Small clock skew is allowed and the amount can be configured — `GATEWAY_JWT_CLOCK_SKEW` (60 s, as identity-service)
- [x] A valid token is forwarded unchanged as `Authorization: Bearer ...` so the service can verify it again
- [x] Identity rotates its signing key (restart without `IDENTITY_TOKEN_SIGNING_KEY`): new tokens are accepted after the JWKS refreshes, without restarting the gateway — an unknown `kid` triggers a refetch; `aNewSigningKeyIsPickedUpWithoutRestart` and in Docker (identity restarted, new token 200, old token 401)
- [x] Public endpoints work with **no** token: register, register-seller, login, refresh, password forgot/reset, seller reapplication (challenge and submit), regions, public sellers, public product reads, and guest cart if Cart allows it. How the gateway knows a path is public is decided and documented: a path allowlist at the gateway, or "verify only when a token is sent" and leave the decision to the service — **allowlist** (team decision 2026-11-02): the 18 PUBLIC endpoints of API v1.1 in `security.PublicEndpoints` (13 Identity, 5 Product); every other routed path needs a valid token (401); unrouted paths stay 404. No guest cart (API §9)
- [x] Decided and documented what happens to an invalid token sent to a public endpoint (reject with 401, or drop it and forward the request as anonymous) — **reject with 401** (team decision), as identity-service does
- [x] The gateway does not decide permissions or roles, does not read `roles` from the token, and does not cache authorization. Whether it also calls `POST /internal/v1/identity/auth/authorization-context` (the spec lists the Gateway as a consumer) is decided. If it does: it needs its own `X-Service-Token`, revoked session or stale `authzVersion` → 401, Identity down → 503 `SERVICE_UNAVAILABLE`, never allowed through — **not called** (team decision, kept on 2026-11-20 although API §7.1 still lists the Gateway as a consumer; SAD §2.4 makes the call optional): each service calls it (API §2.3); a logged-out token passes the gateway and is refused by the service (checked in Docker)

## 4. Headers (API §2.3, §3.10)

- [x] `X-User-Id`, `X-Actor-Id`, `X-Actor-Type`, `X-Permissions` sent by the client are removed before forwarding. Checked with mixed case and with the header repeated, on public and protected routes — `routing.ProxiedRequestHeaders` (every route), `HeadersTests`
- [x] The gateway does not add its own identity headers either; services take `sub` from the verified JWT — the Bearer token is forwarded as sent
- [x] Forwarded unchanged: `Authorization`, `Content-Type`, `Idempotency-Key`, `Accept-Language`, `If-Match` / version headers if used (API §3.8) — also `Cookie` (identity's refresh cookie)
- [x] `X-Forwarded-For` / `X-Forwarded-Proto` / `X-Forwarded-Host` are set by the gateway. A client-supplied `X-Forwarded-For` is not trusted as-is, so identity's per-IP limits (register, forgot-password) see the real client IP and cannot be dodged by faking the header — the gateway is the edge: client `Forwarded` / `X-Forwarded-*` are dropped and `X-Forwarded-For` is exactly the connection's address (plus `-Proto`, `-Host`, `-Port`); the gateway's own appending filters are off. *Identity's view is checked end to end in §10.*
- [x] Hop-by-hop headers (`Connection`, `Transfer-Encoding`, ...) are handled correctly; `Host` is right for the downstream — the gateway's `RemoveHopByHopHeadersFilter` (tested with `Proxy-Authorization`, `Keep-Alive`); `Host` is the instance's

**`X-Request-Id`**
- [x] Missing → the gateway generates one (e.g. UUID) and forwards it — `web.RequestIdFilter`, first filter of all
- [x] Sent by the client in a safe format → kept. Unsafe (does not match `[A-Za-z0-9._-]{1,128}`, the pattern identity-service accepts) → replaced with a generated one — also when sent twice; the service gets exactly one
- [x] Returned in the response header for every request, including gateway errors, 404s and 429s — set at commit, so it also replaces a service's own echo (404, 401, 400, blocked `/internal`, 503 tested; 429 comes with §7)
- [x] In every gateway log line for the request (MDC), and equal to `error.requestId` in gateway errors — Reactor context + `spring.reactor.context-propagation=auto` (MDC key `requestId`, as identity's; `logging.pattern.level` prints it); also on Spring's own lines (`logLinesForTheRequestCarryItsId`)
- [ ] Downstream services receive it and echo the same value (checked against identity-service) — *received: tested with stubs; identity's echo is checked end to end in §10*

## 5. Error envelope (API §3.3, §3.4, §4.1)

- [x] Every error the gateway itself produces uses `{ "error": { code, message, details, requestId } }` with `Content-Type: application/json`; never the Spring Whitelabel page, a default `/error` JSON, or an empty 401/403 body — all go through `error.ErrorWriter`: unknown paths and other statuses (`GatewayErrorHandler`), firewall rejections, blocked `/internal`, 401/503 for tokens, 403 for a refused CORS origin, 413, 429
- [x] Codes come only from API §4.1: `UNAUTHENTICATED`, `TOKEN_EXPIRED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404), `RATE_LIMITED` (429), `INTERNAL_ERROR` (500), `SERVICE_UNAVAILABLE` (503) — plus `VALIDATION_ERROR` for 400 and for statuses without a code of their own (405, 413); `error.ErrorCode`
- [x] Messages in English (team decision, same as identity-service; API §3.3 says Vietnamese); no stack traces, class names or downstream host names in the body — checked in `DownstreamFailureTests` and `JwtTests`
- [x] Request body too large → rejected with a clear error (limit can be configured; status and code decided) — **413 + `VALIDATION_ERROR`**, "The request body must not be larger than 1 MB." (`GATEWAY_MAX_BODY_SIZE`, decision 0002); a declared `Content-Length` is refused before reading, a chunked body while it streams (`web.BodySizeFilter`, `EdgeTests`)

## 6. CORS (API §2.1)

- [x] Allowed origins are exactly the SPA's origin(s) for the environment (one React SPA, ADR-02; dev `http://localhost:5173`), set by config; no `*`; credentials allowed so the refresh cookie works (decision 0001) — `GATEWAY_CORS_ALLOWED_ORIGINS` (comma-separated; a `*` fails startup), `web.CorsFilter`
- [x] A preflight `OPTIONS` from an allowed origin gets 200 with the right `Access-Control-Allow-*` headers, without a token, and is not forwarded to a service — answered before rate limiting and security; `Access-Control-Max-Age` 30 min
- [x] Allowed methods: GET, POST, PUT, PATCH, DELETE. Allowed request headers: `Authorization`, `Content-Type`, `Idempotency-Key`, `X-Request-Id`, `Accept-Language` — also `If-Match` (API §3.8); a preflight for anything else is refused
- [x] `X-Request-Id` is in `Access-Control-Expose-Headers` so the browser can read it — with `Retry-After`, `Idempotent-Replayed` (API §3.7) and `Location`
- [x] A disallowed origin gets no `Access-Control-Allow-Origin` header — its cross-origin requests (and preflights) are refused with 403 `FORBIDDEN` in the envelope and never forwarded
- [x] CORS headers are not duplicated: the gateway sets them and the services don't (or downstream values are removed) — a service's `Access-Control-*` are dropped and its `Vary` merged (`routing.ProxiedResponseHeaders`)
- [x] CORS headers are present on error responses too (401, 404, 429, 503), so the browser can show the error — set before anything writes the response (`gatewayErrorsCarryTheCorsHeadersToo`, 429 in `RateLimitTests`)

## 7. Rate limiting (API §3.4 N-04)

N-04 gives no numbers yet; the values are the team's proposal in decision 0002 (`docs/decisions/0002-gioi-han-tan-suat-gateway.md`) and must be configurable.

- [x] Per-client-IP limit on public routes; 429 `RATE_LIMITED` in the error envelope, with `Retry-After` — every request except `/actuator/health` and preflights: 300/min per IP (`web.RateLimitFilter`, token bucket); `Retry-After` in whole seconds
- [x] Stricter limits on sensitive endpoints: `POST /api/v1/identity/auth/login`, `/register`, `/register-seller`, `/refresh`, `/password/forgot`, `/password/reset`, `/seller-reapplication/challenge`, `/seller-reapplication` (10/min per IP; others 300/min per IP, decision 0002) — counted in their own tier (`RateLimitTests`)
- [ ] Optional per-user limit (by `sub`) on authenticated routes — *not built (optional; decision 0002 limits per IP only)*
- [x] The client IP is the real one (see `X-Forwarded-For` above); faking the header does not reset the counter — the connection's address (`fakingTheForwardedForHeaderDoesNotResetTheCount`)
- [x] Limits and windows set by properties; how counters work with more than one gateway instance is decided (in-memory is fine for the MVP, and written down) — `GATEWAY_RATE_LIMIT_{SENSITIVE,GENERAL}_{REQUESTS,WINDOW}`; per instance, so N instances allow up to N times the limit (decision 0002, SAD C.5)
- [x] How it fits identity-service's own per-IP limits (register, forgot-password) is decided; requests under the gateway limit are not rejected without reason — both apply: the gateway's is a coarse per-minute guard, identity keeps its own per-hour limits, which now see the real client IP (§4)

## 8. Security hygiene

- [ ] `Authorization` header values, tokens, passwords and reset tokens never appear in gateway logs (request logging masks them)
- [ ] Access log: method, path, status, duration, `requestId` and client IP for every request; no request bodies
- [ ] No server/version headers that leak the stack (`Server`, `X-Powered-By`)
- [ ] Stateless: no session and the gateway sets no cookies of its own; identity's refresh-token cookie (`Cookie` / `Set-Cookie`) passes through unchanged. CSRF protection for that cookie is identity's Origin check (decision 0001), so the gateway's CSRF stays off

## 9. Docker and compose

- [ ] `./up.sh` builds and starts the gateway; it waits for discovery-service to be healthy and registers with Eureka
- [ ] In compose the JWKS URL and Eureka URL point to service names, not localhost
- [ ] The team's dev image (`mvn spring-boot:run`, `src` mounted read-only) starts the gateway and a compose health check on `/actuator/health` passes. *A release image (non-root, built jar) is out of scope for now (system-architecture §2.5).*
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
- [ ] `GET /api/v1/products` and `GET /api/v1/categories` reach product-service (path without `/api/v1`). Routes for services not built yet (Cart, Order, Payment, Finance, Notification) are checked once each service has an endpoint; until then 503 is expected
