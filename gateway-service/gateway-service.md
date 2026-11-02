# gateway-service — Progress

API Gateway for the platform: routing, JWT verification, stripping client-supplied identity headers, blocking `/internal/**`, rate limiting and CORS (see `docs/api.docx` §2.2–2.3). The definition of done is in `gateway-service-checklist.md`.

## Done

- **Scaffold**: Spring Boot 4.1.2, Java 21, Spring Cloud 2025.1.4, package `com.example.gateway_service`.
- **Web stack: servlet.** `spring-cloud-starter-gateway-server-webmvc`, the same model as identity-service. `spring-boot-starter-webflux` and its test starter were removed, so routes, filters and security use the servlet/MVC APIs.
- **Dependencies** (`pom.xml`):
  - `spring-cloud-starter-gateway-server-webmvc` for routing
  - `spring-boot-starter-security` and `spring-boot-starter-security-oauth2-resource-server` for JWT checks
  - `spring-cloud-starter-netflix-eureka-client` for service discovery
  - `spring-boot-starter-actuator` for health checks
  - test starters: actuator, security, oauth2-resource-server, webmvc (MockMvc)
- **Config** (`application.properties`): `spring.application.name=gateway-service`, `server.port=8081`, Eureka at `http://localhost:8761/eureka/`. Actuator exposes only `health`, with no details or components, and the `/actuator` index page is off.
- **Docker**:
  - Two-stage Dockerfile: Maven build, then `eclipse-temurin:21-jre`, running as UID 10001 and exposing 8081.
  - `.dockerignore`.
- **Compose**: uses the `x-eureka-client` anchor but overrides `depends_on` (only `discovery-service`) and `environment` (only the Eureka URL via `SPRING_APPLICATION_JSON`, anchor `*eureka-url-json`), because the gateway has no database. Published on port 8081.
- **Security** (`security.SecurityConfig`): stateless, with no CSRF, HTTP session, request cache, form login, basic auth or logout. It lets every request through for now; that's only safe because there are no routes yet. JWT verification replaces it in checklist §3.
- **Tests** (`GatewayServiceApplicationTests`, no Eureka, no database): no `DataSource`, public detail-free health, other actuator endpoints 404, `/hello` gone, no session cookie or login challenge.
- **Checklist step 0 (Foundation)** done except the externalized-settings item, which finishes as the later steps add their properties.
- **Routing** (checklist §1, `routing` package):
  - `ServiceRoute` is the API §2.2 table (service id → public paths); `RoutesConfig` builds one route per service: any method, path unchanged, instance chosen through Eureka (`lb(...)` + `http()`).
  - `LoadBalancingConfig` / `DiscoveryInstances`: instances come straight from the discovery client, with no LoadBalancer cache (`spring.cloud.loadbalancer.cache.enabled=false`).
  - `DownstreamErrors`: no instance, connection refused or read timeout → 503 `SERVICE_UNAVAILABLE`; anything else → 500 `INTERNAL_ERROR` (logged). It is registered as the first route filter, because the builder's `onError` only wraps the handler, not the `lb` filter.
  - Timeouts are Spring Boot's `spring.http.clients.*`: connect `GATEWAY_CONNECT_TIMEOUT` (2 s), read `GATEWAY_READ_TIMEOUT` (30 s).
- **Errors** (`error` package): `ErrorResponse` / `ErrorCode` (the API §4.1 codes the gateway uses) and `GatewayErrorController`, which replaces Boot's `/error` so that 404s and other gateway-side errors use the envelope. `web.RequestIds` reads a safe `X-Request-Id` (identity-service's pattern); generating one is §4. Static resource mappings are off and `PageNotFound` warnings are muted.
- **Routing tests**: `RoutingTests` (every prefix, 404s, pass-through of methods, query, body, headers and downstream statuses/bodies, round robin over two instances) and `DownstreamFailureTests` (refused, no instance, timeout), against JDK-`HttpServer` stubs (`support.StubService`) over real HTTP. 59 tests.

## Not implemented yet

- [ ] JWT resource-server config: issuer, audience, signing key or JWKS from identity-service
- [ ] Remove client-sent `X-User-Id`, `X-Actor-Id`, `X-Actor-Type` and `X-Permissions` headers; forward `Authorization` and `X-Request-Id`, generating `X-Request-Id` when the client doesn't send one (services don't generate it)
- [ ] Reject external requests to `/internal/**`
- [ ] CORS for the 3 web apps; rate limiting
- [ ] Standard error envelope (`{ "error": { code, message, details, requestId } }`) for gateway-level failures

## Notes / open issues

- The gateway stays on **8081**; the API spec's suggested base URL `http://localhost:8080/api/v1` doesn't apply here (8080 is cart-service). The FE base URL is `http://localhost:8081/api/v1` (`DEVELOPMENT.md`).
- A crashed instance stays in Eureka until its lease expires (about 90 s) and requests sent to it get 503; there is no retry on another instance yet (checklist §1, open).
- Until JWT verification (§3) the gateway forwards every routed request without checking the token; services still verify it themselves.

## Log

| Date | Change |
|---|---|
| 2026-10-28 | Recorded the initial state: scaffold, Docker image and compose entry |
| 2026-10-28 | Added the `/hello` endpoint and a temporary `SecurityConfig`; made `mvnw` executable in git |
| 2026-10-29 | Removed the Maven wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`, `.gitattributes`); build and test with a locally installed `mvn` |
| 2026-11-02 | Added `gateway-service-checklist.md` (routing, `/internal/**` block, JWT, headers and `X-Request-Id`, error envelope, CORS, rate limits, Docker, end-to-end) |
| 2026-11-02 | Checklist step 0: servlet stack only (webflux removed), stateless `SecurityConfig` replaces the temporary one and `HelloController`, health-only actuator without details, compose without database dependency or `SPRING_DATASOURCE_*`, foundation tests; FE base URL documented as 8081 |
| 2026-11-02 | Checklist step 1 (routing): API §2.2 route table through Eureka with paths unchanged, 404 envelope for everything else (`GatewayErrorController`), 503 `SERVICE_UNAVAILABLE` for no instance / refused / timeout, configurable timeouts, no LoadBalancer cache; 59 tests; register → login → me → refresh → logout checked through the gateway in Docker |
