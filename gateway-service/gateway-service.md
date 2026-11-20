# gateway-service — Progress

API Gateway for the platform: routing, JWT verification, stripping client-supplied identity headers, blocking `/internal/**`, rate limiting and CORS (see `docs/requirements/api_specs.md` §2.1–2.3). The definition of done is in `gateway-service-checklist.md`. Work plan: `docs/plans/active/identity-gateway-alignment.md`.

## Done

- **Scaffold**: Spring Boot 4.1.2, Java 21, Spring Cloud 2025.1.4, package `com.example.gateway_service`.
- **Web stack: reactive (WebFlux on Netty).** `spring-cloud-starter-gateway-server-webflux`, as the accepted baseline requires (SAD ADR-02, `docs/engineering.md` REQ-ENG-002). Routes, filters and security use the reactive APIs; there is no servlet container or Spring MVC on the classpath (`runsOnTheReactiveStackOnly`). Until 2026-11-20 it was servlet-based (WebMVC).
- **Dependencies** (`pom.xml`):
  - `spring-cloud-starter-gateway-server-webflux` for routing
  - `spring-boot-starter-security` and `spring-boot-starter-security-oauth2-resource-server` for JWT checks
  - `spring-cloud-starter-netflix-eureka-client` for service discovery
  - `spring-boot-starter-actuator` for health checks
  - test starters: actuator, security, oauth2-resource-server, webflux
- **Config** (`application.properties`): `spring.application.name=gateway-service`, `server.port=8081`, Eureka at `http://localhost:8761/eureka/`. Actuator exposes only `health`, with no details or components, and the `/actuator` index page is off.
- **Docker**:
  - The team's single-stage dev image (`maven:3.9-eclipse-temurin-21`, `mvn spring-boot:run`), exposing 8081; the root `compose.yaml` mounts `src` read-only.
  - `.dockerignore`.
- **Compose**: uses the `x-eureka-client` anchor but overrides `depends_on` (only `discovery-service`) and `environment` (only the Eureka URL via `SPRING_APPLICATION_JSON`, anchor `*eureka-url-json`), because the gateway has no database. Published on port 8081.
- **Security** (`security.SecurityConfig`, a `SecurityWebFilterChain`): stateless (no-op security context repository and request cache), with no CSRF, form login, basic auth or logout.
- **Tests** (`GatewayServiceApplicationTests`, no Eureka, no database): no `DataSource`, public detail-free health, other actuator endpoints 404, `/hello` gone, no session cookie or login challenge.
- **Checklist step 0 (Foundation)** done except the externalized-settings item, which finishes as the later steps add their properties.
- **Routing** (checklist §1, `routing` package):
  - `ServiceRoute` is the API §2.2 table (service id → public paths); `RoutesConfig` builds one route per service with `RouteLocatorBuilder`: any method, instance chosen through Eureka (`lb://<service>`), path unchanged except for Product: product-service serves `/products`, `/categories`, ... without the base path, so its route drops `/api/v1` (`stripPrefix(2)`, query kept as sent; team decision 2026-11-20).
  - `LoadBalancingConfig` / `DiscoveryInstances`: instances come straight from the (reactive) discovery client, with no LoadBalancer cache (`spring.cloud.loadbalancer.cache.enabled=false`).
  - Timeouts: connect `GATEWAY_CONNECT_TIMEOUT` (2 s, `gateway.proxy.connect-timeout`, applied by an `HttpClientCustomizer`), response `GATEWAY_READ_TIMEOUT` (30 s, `spring.cloud.gateway.server.webflux.httpclient.response-timeout`).
- **Errors** (`error` package): `ErrorResponse` / `ErrorCode` (the API §4.1 codes the gateway uses), `ErrorWriter` (writes the envelope with the request id; every gateway-side error goes through it) and `GatewayErrorHandler`, an `ErrorWebExceptionHandler` ahead of Boot's: no instance, connection refused or response timeout (504 inside the gateway) → 503 `SERVICE_UNAVAILABLE`; no route → 404 `NOT_FOUND`; other statuses keep their status; anything else → 500 `INTERNAL_ERROR` (logged). `web.RequestIds` reads a safe `X-Request-Id` (identity-service's pattern); generating one is §4. Static resource mappings are off.
- **`/internal/**` blocked** (checklist §2): `security.InternalPathFilter`, a `WebFilter` first in the security chain, answers 404 `NOT_FOUND` (like an unknown path, so nothing is revealed) to any path with a segment `internal`, in any case and after percent-decoding. Encoded `/`, `\`, `.`, `;`, `//` and `../` are rejected with 400 `VALIDATION_ERROR` before that by Spring Security's `StrictServerWebExchangeFirewall` (its `ServerExchangeRejectedHandler` writes the envelope). A client-sent `X-Service-Token` is removed from every proxied request.
- **Access tokens** (checklist §3, `security` package; team decisions 2026-11-02):
  - `JwtConfig`: a `NimbusReactiveJwtDecoder`, RS256 only, signature against identity-service's JWKS (`gateway.jwt.*`, fetched on the first token over a `WebClient` with `gateway.jwt.jwks-timeout`, cached, refetched for an unknown `kid`), `exp` with 60 s skew, `iss`, `aud`, non-blank `sub` and `sessionId`. Session state and permissions are left to each service (the gateway does not call authorization-context).
  - `PublicEndpoints`: API v1.1's 18 PUBLIC endpoints (13 Identity, including seller reapplication challenge/submit, and 5 Product) are the only routed requests forwarded without a token; every other routed path needs a valid one. Unrouted paths stay 404.
  - Any `Authorization` header is checked, even on public endpoints: a bad or malformed one is a 401 (`StrictBearerTokenConverter`: exactly one `Bearer <token>`, no query-string tokens).
  - `AuthenticationErrors`: 401 `UNAUTHENTICATED` / `TOKEN_EXPIRED` in the envelope with `WWW-Authenticate`; JWKS unreachable → 503 `SERVICE_UNAVAILABLE`. Tokens are never logged.
- **Token tests**: `JwtTests` (public vs protected routes, 19 bad tokens, expiry and skew, malformed headers, forwarding, key rotation) and `JwksUnavailableTests`, with test keys and a JWKS stub (`support.TestTokens`).
- **Routing tests**: `RoutingTests` (every prefix, 404s, pass-through of methods, query, body, headers and downstream statuses/bodies, round robin over two instances) and `DownstreamFailureTests` (refused, no instance, timeout), against JDK-`HttpServer` stubs (`support.StubService`) over real HTTP. All gateway tests are black-box over real HTTP (`support.GatewayClient`), so they carried over unchanged when the stack moved to WebFlux.

- **Request headers** (checklist §4, `routing.ProxiedRequestHeaders`, the last request-header filter on every route): client-sent `X-User-Id`, `X-Actor-Id`, `X-Actor-Type`, `X-Permissions` and `X-Service-Token` are removed; `Forwarded` / `X-Forwarded-*` from the client are dropped and `X-Forwarded-For` is exactly the connection's address (plus `-Proto`, `-Host`, `-Port`), so per-IP limits downstream see the real client. The gateway's own forwarded-header filters are off.
- **Request id** (`web.RequestIdFilter`, before every other filter): a single safe `X-Request-Id` from the client is kept, anything else is replaced by a UUID; it is forwarded, set on every response at commit (errors too), equals `error.requestId` (`ErrorWriter` reads it from the exchange) and is in the MDC (`requestId`, printed by `logging.pattern.level`) through Reactor context propagation (`io.micrometer:context-propagation`, `spring.reactor.context-propagation=auto`); error handlers outside the request context use `RequestIds.withMdc`. Tests: `HeadersTests`.

- **Edge filters** (`web` package, all before security, right after the request id; values in `EdgeProperties`, decisions 0001/0002):
  - `CorsFilter`: the SPA's exact origins (`GATEWAY_CORS_ALLOWED_ORIGINS`, dev `http://localhost:5173`) with credentials; preflights answered here, other origins refused with 403 `FORBIDDEN`; headers set before any error is written. `routing.ProxiedResponseHeaders` drops services' own `Access-Control-*` and merges `Vary`.
  - `RateLimitFilter`: in-memory token buckets per client IP and instance; sensitive auth endpoints 10/min, everything else 300/min (health and preflights not counted); 429 `RATE_LIMITED` with `Retry-After`.
  - `BodySizeFilter`: bodies over 1 MB → 413 `VALIDATION_ERROR` (declared length refused before reading, chunked bodies counted while streaming; `error.GatewayRejection`).
  - Tests: `EdgeTests`, `RateLimitTests`.

## Not implemented yet

- [ ] Per-user (`sub`) rate limit (optional)

## Notes / open issues

- The gateway does not call `POST /internal/v1/identity/auth/authorization-context` although API §7.1 lists the Gateway as a consumer (team decision 2026-11-02, kept 2026-11-20; SAD §2.4 makes it optional).
- The gateway stays on **8081**; the API spec's suggested base URL `http://localhost:8080/api/v1` doesn't apply here (8080 is cart-service). The FE base URL is `http://localhost:8081/api/v1` (`DEVELOPMENT.md`).
- A crashed instance stays in Eureka until its lease expires (about 90 s) and requests sent to it get 503; there is no retry on another instance yet (checklist §1, open).
- A token whose session was revoked (logout, password change) still passes the gateway until it expires; the service refuses it after asking Identity (team decision: no authorization-context call at the gateway).
- `PublicEndpoints` must follow the spec: a new PUBLIC endpoint is a 401 at the gateway until it is added there.
- In compose every service also publishes its own port on all host interfaces (`8083:8083` etc.), so `/internal` is reachable around the gateway in dev (identity still demands `X-Service-Token`). That is a dev shortcut; in a deployment only the gateway is exposed. Binding those ports to `127.0.0.1` in `compose.yaml` would limit it to the dev machine.

## Log

| Date | Change |
|---|---|
| 2026-10-28 | Recorded the initial state: scaffold, Docker image and compose entry |
| 2026-10-28 | Added the `/hello` endpoint and a temporary `SecurityConfig`; made `mvnw` executable in git |
| 2026-10-29 | Removed the Maven wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`, `.gitattributes`); build and test with a locally installed `mvn` |
| 2026-11-02 | Added `gateway-service-checklist.md` (routing, `/internal/**` block, JWT, headers and `X-Request-Id`, error envelope, CORS, rate limits, Docker, end-to-end) |
| 2026-11-02 | Checklist step 0: servlet stack only (webflux removed), stateless `SecurityConfig` replaces the temporary one and `HelloController`, health-only actuator without details, compose without database dependency or `SPRING_DATASOURCE_*`, foundation tests; FE base URL documented as 8081 |
| 2026-11-02 | Checklist step 1 (routing): API §2.2 route table through Eureka with paths unchanged, 404 envelope for everything else (`GatewayErrorController`), 503 `SERVICE_UNAVAILABLE` for no instance / refused / timeout, configurable timeouts, no LoadBalancer cache; 59 tests; register → login → me → refresh → logout checked through the gateway in Docker |
| 2026-11-02 | Checklist step 2 (`/internal/**`): `InternalPathFilter` blocks any `internal` path segment (any case, decoded) with 404 before token checks; encoding tricks rejected with 400 (16 variants tested); `X-Service-Token` stripped from proxied requests; Tomcat's own error page replaced by the JSON envelope; 95 tests |
| 2026-11-02 | Checklist step 3 (JWT): RS256 tokens checked against identity-service's JWKS (lazy, cached, refetched on a new `kid`), `exp` with skew, `iss`, `aud`, `sub`, `sessionId`; spec PUBLIC allowlist, bad tokens rejected even on public endpoints, no authorization-context call (team decisions); 401 `UNAUTHENTICATED` / `TOKEN_EXPIRED`, 503 when keys can't be fetched; 173 tests; real tokens and a key rotation checked in Docker |
| 2026-11-20 | Moved to the reactive stack (SAD ADR-02 / REQ-ENG-002): `spring-cloud-starter-gateway-server-webflux` on Netty, `SecurityWebFilterChain`, `NimbusReactiveJwtDecoder`, `WebFilter` for `/internal/**`, one `ErrorWebExceptionHandler` + `ErrorWriter` for every gateway error (replacing `GatewayErrorController`, `DownstreamErrors` and the Tomcat valve), firewall rejections as a 400 envelope. Every existing black-box test passes unchanged (only the timeout property name moved); `GatewayServiceApplicationTests` uses real HTTP instead of MockMvc and checks no servlet stack is present; 174 tests |
| 2026-11-20 | API v1.1 routing: `CATALOG` → `PRODUCT`, Product's route drops `/api/v1` (product-service serves paths without it), seller-reapplication challenge/submit added to `PublicEndpoints` (18), internal-path tests use the v1.1 prefixes (`/internal/v1/products/**`, `/inventory/**`, obligations); checklist references moved to `docs/requirements/` and outdated items (three web apps, Bearer-only, UID 10001) rewritten; 181 tests |
| 2026-11-20 | Checklist step 4 (headers, request id): identity headers and `X-Service-Token` removed, `X-Forwarded-*` rebuilt from the connection, request id kept/generated, forwarded, on every response and error body, in the MDC across Reactor threads; 201 tests |
| 2026-11-20 | Checklist steps 5–7: every gateway error in the envelope (also 403 CORS, 413, 429), 1 MB body limit (413 `VALIDATION_ERROR`), CORS for the SPA with credentials (preflight at the gateway, other origins 403, headers on errors, services' CORS headers dropped), per-IP rate limits (sensitive 10/min, general 300/min, `Retry-After`); 231 tests |
