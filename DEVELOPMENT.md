# Running the app for development

## Prerequisites

- Docker with the Compose plugin
- Java 21, only needed to run or test a service outside Docker (the Docker build brings its own Maven). gateway-service and identity-service need a locally installed Maven 3.8+ (`mvn`); the other services ship a Maven wrapper that is not executable in git, so run it as `sh mvnw` (or use `mvn`).
- Free host ports: `8333` (object storage), `8761` (Eureka), `8080`, `8081`, `8084`–`8087`, `18082` (Finance), `18083` (Identity). Port `5432` is only needed for optional local PostgreSQL.

Compose requires `.env` in the repository root. If it does not exist, copy
`.env.example` to `.env` and fill in `DB_PASSWORD`. The template contains no
password; `.env` and other local environment files are ignored by Git.

Always pass the same compose project name, `-p ecommerce`. `up.sh` builds images named `ecommerce-<service>`, which is what compose expects for that project name.

## Option 1 — Run everything in Docker

```bash
bash up.sh
```

This builds an image for every `*-service/` folder, then starts the stack with `docker compose -p ecommerce up -d --no-build`. Services wait for healthy Discovery and connect directly to the database in `.env`; the optional local `database` service is inactive by default.

Check that it's up:

```bash
docker compose -p ecommerce ps
curl -s http://localhost:18083/actuator/health   # Identity's published Docker port
curl -s http://localhost:8081/actuator/health    # gateway: {"status":"UP"} only, no details
curl -s http://localhost:18083/hello
```

Open the Eureka dashboard at http://localhost:8761 to see which services have registered.

| Service | URL |
|---|---|
| discovery-service (Eureka) | http://localhost:8761 |
| cart-service | http://localhost:8080 |
| gateway-service | http://localhost:8081 |
| finance-service | http://localhost:18082 |
| identity-service | http://localhost:18083 |
| notification-service | http://localhost:8084 |
| order-service | http://localhost:8085 |
| payment-service | http://localhost:8086 |
| product-service | http://localhost:8087 |

The front-end base URL is the gateway: `http://localhost:8081/api/v1`. The API spec (§3.1) suggests `http://localhost:8080/api/v1`, but 8080 is cart-service here, so use 8081. What the gateway does with a request (checklist and details in `gateway-service/gateway-service-checklist.md`):

- **Routing**: `/api/v1/...` goes to the services through Eureka (route table in `gateway-service/src/main/java/com/example/gateway_service/routing/ServiceRoute.java`). product-service gets its paths without `/api/v1` (`/api/v1/products` → `/products`); the others get the full path. A service that isn't running answers `503 SERVICE_UNAVAILABLE`; unknown paths and anything under `/internal` are `404`.
- **Tokens**: every routed path except the spec's PUBLIC endpoints (login, register, refresh, password reset, seller reapplication, regions, public sellers, product browsing) needs `Authorization: Bearer <accessToken>`, else `401`. The gateway reads identity-service's keys from `GATEWAY_JWT_JWK_SET_URI` (compose sets it), so it keeps working when identity-service restarts with a new temporary key.
- **CORS**: only the SPA's origin, `http://localhost:5173` by default (`GATEWAY_CORS_ALLOWED_ORIGINS`, comma-separated), with credentials so the refresh cookie is sent. Calls from any other origin get `403`.
- **Limits** (per client IP, per gateway instance): the sign-in/sign-up/refresh/password/reapplication endpoints 10 requests per minute, everything else 300 per minute, then `429` with `Retry-After`; request bodies up to 1 MB (`413` above). All configurable with `GATEWAY_RATE_LIMIT_*` and `GATEWAY_MAX_BODY_SIZE`. If you hit the limits while testing, restart the gateway to reset its counters.
- **Request id**: every response has an `X-Request-Id` (yours if you send a safe one, else a generated UUID); the same id is in the gateway's and the services' log lines (`requestId=...`) and in error bodies.
- **Headers**: client-sent `X-User-Id`, `X-Actor-*`, `X-Permissions`, `X-Service-Token` and `X-Forwarded-*` are dropped; services get `X-Forwarded-For` with the real client address.

Each service's own port still works for direct calls in dev; that is a dev shortcut (it also exposes `/internal` around the gateway, still protected by `X-Service-Token`), and in a deployment only the gateway is reachable.

### Rebuild one service after a code change

```bash
docker build --network=host -t ecommerce-identity-service identity-service
docker compose -p ecommerce up -d --no-build identity-service
```

Running `bash up.sh` again rebuilds and recreates services as needed. After changing `.env`, use `docker compose -p ecommerce up -d` to apply the new environment; `restart` does not reload it.

### Logs

```bash
docker compose -p ecommerce logs -f identity-service
```

## Option 2 — Run a service locally, with infrastructure in Docker

Start Eureka and object storage (no local database is needed for Supabase):

```bash
docker compose -p ecommerce up -d discovery-service object-storage
```

Load `.env` and export Spring's datasource variables from the repository root:

```bash
set -a
. ./.env
set +a
export SPRING_DATASOURCE_URL="$DB_JDBC_URL"
export SPRING_DATASOURCE_USERNAME="$DB_USERNAME"
export SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"
cd identity-service
mvn spring-boot:run
```

Set the same three `SPRING_DATASOURCE_*` environment variables in each business
service's IDE run configuration. Java 21 is required. Host-run Identity listens
on `8083`, Finance on `8082`; the Docker host ports above are different.
Neither Spring Boot nor Maven automatically loads the root `.env`.
`application.properties` points Eureka at `http://localhost:8761/eureka/`;
schema ownership remains unchanged.

## Running tests

```bash
cd identity-service
mvn test
```

identity-service tests start their own throwaway PostgreSQL container (Testcontainers), so Docker must be running, but the compose stack doesn't need to be.

You can also skip Docker for Eureka entirely by running it with `sh mvnw spring-boot:run` inside `discovery-service/`.

Don't mix modes for services that call each other. A service started on the host registers in Eureka with the host's address, which containers such as the gateway may not be able to reach. Either run all the interacting services on the host, or run them all in Docker.

## Database

Compose maps `DB_JDBC_URL`, `DB_USERNAME` and `DB_PASSWORD` from `.env` into
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD` for all seven business services. Missing/empty
values fail Compose configuration instead of silently using another database.
Do not print `docker compose config` output or share container environment
dumps: they contain the resolved password. Use `docker compose config --quiet`.

The Supabase JDBC URL is
`jdbc:postgresql://db.eqpnceiiqruzpmlmzhuh.supabase.co:5432/postgres?sslmode=require`.
Keep credentials outside the URL. This direct endpoint requires connectivity
from the machine/container running the service; the current development
credential is the database's `postgres` user, not a Supabase API key.

The Supabase smoke check below ran Product on the **host**, not inside Docker.
The direct endpoint normally needs IPv6; Compose configuration validation
does not prove connectivity from a Docker bridge. If containers cannot reach
it, copy the **Session Pooler** connection details (IPv4, port `5432`) from
the Supabase dashboard's Connect dialog. Set `DB_JDBC_URL` to that pooler's
host/database with `sslmode=require`, and `DB_USERNAME` to the exact pooler
username shown there; it differs from the direct endpoint's `postgres` user.
Do not guess the pooler hostname or substitute the transaction pooler on
port `6543`. Recreate containers after updating `.env`.

| Service | Schema |
|---|---|
| Identity | `identity` |
| Product | `public` |
| Cart | `cart` |
| Order | `orders` |
| Payment | `payment_service` |
| Finance | `finance` |
| Notification | `notification` |

Keep Flyway enabled. Product keeps its dedicated `product_schema_history`
table and uses Hibernate `validate`, not automatic schema updates. Its legacy
`DataSeeder` is disabled by default (`product.seed.enabled=false`); only opt in
with `PRODUCT_SEED_ENABLED=true` in the Product process environment on a
disposable database. That dataset adds its own categories, products and reviews
and must not be enabled on the seeded Supabase database. The Product base
catalog tables must already exist before Hibernate validation can pass.

For optional local PostgreSQL, explicitly start it with
`docker compose -p ecommerce --profile local-db up -d database`. It publishes
`127.0.0.1:${POSTGRES_HOST_PORT:-5432}` and keeps data in `postgres-data`.
Starting that profile does **not** switch application datasources: set all three
`DB_*` values in `.env` to the local database first if using it for Compose
applications (`jdbc:postgresql://database:5432/ecommerce`, username/password
`ecommerce`). Wait for the local database to be healthy before starting apps;
they no longer depend on its health check. For host-run apps, use
`jdbc:postgresql://localhost:<published-port>/ecommerce` instead. Do not remove
the local volume when switching databases.

Verification on 25/11/2026: Compose resolved the same Supabase datasource for
all seven business services, excluded local PostgreSQL by default and rejected
an empty password. An isolated Product process connected to Supabase, passed
Flyway/Hibernate validation, returned HTTP health `UP` and listed 10 categories.
Database counts remained 100 products, 5 sellers and no reviews: the legacy
seeder did not run. This is not a full-stack checkout or interservice proof.

## Object storage

`object-storage` runs SeaweedFS with an S3 API on `http://localhost:8333` (set `S3_HOST_PORT` to move it, like `POSTGRES_HOST_PORT`). Dev credentials are in `identity-service/infra/seaweedfs/s3.json` (`ecommerce` / `ecommerce-secret`), and files live in the `object-storage-data` volume. identity-service creates its bucket `ecommerce-media` on first use.

Browsers upload straight to presigned URLs, so they must reach the storage at the address the URLs are signed for: `IDENTITY_MEDIA_PUBLIC_ENDPOINT` (default `http://localhost:8333`). Inside Docker, identity itself uses `IDENTITY_MEDIA_ENDPOINT=http://object-storage:8333`. When running identity on the host, start the storage with `docker compose -p ecommerce up -d object-storage`.

## Regions

The address book and `GET /api/v1/identity/regions/**` read the provinces and wards seeded in identity's own database by migration V8 (provinces.open-api.vn v2, fetched 2026-11-20: 34 provinces, 3,321 wards); nothing external is called. Customers keep at most 3 addresses, and deleting one removes it.

```bash
curl -s http://localhost:8083/api/v1/identity/regions/provinces
curl -s http://localhost:8083/api/v1/identity/regions/provinces/01/wards
```

## Admin account

No public endpoint creates admins. identity-service creates one at startup when `identity.bootstrap.admin.email` is set; the password must meet G-07 (≥ 8 characters with an uppercase letter, a lowercase letter and a digit). If a user with that email already exists, nothing changes.

```bash
cd identity-service
IDENTITY_BOOTSTRAP_ADMIN_EMAIL=admin@example.test \
IDENTITY_BOOTSTRAP_ADMIN_PASSWORD='Admin1234' \
mvn spring-boot:run
```

The Docker compose entry doesn't pass these variables yet.

The admin API is under `/api/v1/identity/admin` (lists, seller approve/reject, ban/unban). A ban has no scope: it reads the account's obligations from order-service (`IDENTITY_ORDER_BASE_URL`, default `http://localhost:8085`) and, for a seller, finance-service (`IDENTITY_FINANCE_BASE_URL`, default `http://localhost:8082`); open obligations → BANNED with a limited session, none → DISABLED for a 30-day restore window. Neither service implements its obligations endpoint yet, so bans, and sign-ins of banned accounts, return 503 and change nothing until they do. J7 reconciles banned accounts every `IDENTITY_BAN_RECONCILIATION_INTERVAL` (default 10 minutes).

```bash
B=http://localhost:8083/api/v1/identity
T=<admin access token from /auth/login>
curl -s -H "Authorization: Bearer $T" "$B/admin/sellers?approvalStatus=PENDING&sort=registeredAt,asc"
curl -s -H "Authorization: Bearer $T" -X POST $B/admin/sellers/<sellerId>/approve
curl -s -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"scope":"SELLER","reason":"Counterfeits"}' $B/admin/ban/<userId>
```

## Tokens

identity-service signs access tokens with an RSA key from `IDENTITY_TOKEN_SIGNING_KEY` (a PKCS#8 PEM private key). Without it, a temporary key is generated at startup and logged as a warning: fine for local work, but every restart logs everyone out. To use a fixed key on the host:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/identity-signing-key.pem
IDENTITY_TOKEN_SIGNING_KEY="$(cat /tmp/identity-signing-key.pem)" mvn spring-boot:run
```

Google sign-in for customers (`POST /api/v1/identity/auth/google`) is off until `IDENTITY_GOOGLE_CLIENT_ID` is set to the web app's Google OAuth client id (then the SPA sends the ID token from Google Identity Services); without it the endpoint returns 503.

Password reset emails link to `IDENTITY_PASSWORD_RESET_LINK_BASE` (default `http://localhost:5173/reset-password`) with `?token=...` appended; set it to the FE's reset page.

Quick check of the auth flow against identity-service directly (with the stack in Docker, the same paths also work through the gateway: `B=http://localhost:8081/api/v1/identity/auth`):

```bash
B=http://localhost:8083/api/v1/identity/auth
curl -s -H 'Content-Type: application/json' -d '{"email":"an@example.test","fullName":"An","phoneNumber":"0912345678","password":"Secret123","confirmPassword":"Secret123"}' $B/register
curl -s -c /tmp/jar -H 'Content-Type: application/json' -d '{"email":"an@example.test","password":"Secret123","accountType":"CUSTOMER"}' $B/login
curl -s -b /tmp/jar -c /tmp/jar -H 'Origin: http://localhost:5173' -X POST $B/refresh
```

The refresh token is never in a response body: login and refresh set it as the HttpOnly cookie `refresh_token` (Secure, SameSite=Strict, Path `/api/v1/identity/auth`), and refresh/logout read only that cookie. Both also need an `Origin` from `IDENTITY_ALLOWED_ORIGINS` (default `http://localhost:5173`), otherwise 403; the SPA calls them with `credentials: "include"`. Browsers accept Secure cookies on `http://localhost`; for anything else over plain http, run identity with the `local` profile (`SPRING_PROFILES_ACTIVE=local`), which turns Secure off.

Every role is its own account (API v1.1 §3.2.1): login names the web app's `accountType` (`CUSTOMER`, `SELLER` or `ADMIN`), and the same email and password on another channel is refused like a wrong password. A seller signs in only after an admin approved the application.

## Running identity-service alone

To run only identity-service (with its own PostgreSQL and storage, no Eureka), from the published image `hnan756/identity-service` or built locally, follow [`identity-service/RUN.md`](identity-service/RUN.md). It uses `identity-service/compose.yaml` and the same host ports 8083/8333 as this stack, so stop one before starting the other.

## API docs (identity-service)

- `identity-service/api.html`: readable docs, open it in a browser (it loads the Redoc script from a CDN).
- `identity-service/openapi.json`: the OpenAPI 3.1 spec. In Postman: **Import → File → openapi.json**. It creates a collection with `baseUrl` = `http://localhost:8083`; set the collection's Bearer token to the `accessToken` from `POST /api/v1/identity/auth/login` (and an `X-Service-Token` header for `/internal` calls).
- While identity-service runs: Swagger UI at http://localhost:8083/swagger-ui.html (try requests there) and the live spec at http://localhost:8083/v3/api-docs (Postman can also import it by URL). Set `IDENTITY_API_DOCS_ENABLED=false` to turn both off.

Both files are generated from the code by `OpenApiDocsTests`, so they change only when an endpoint does. After changing endpoints, run `mvn test -Dtest=OpenApiDocsTests` in `identity-service` and commit the regenerated files.

## Internal API (service to service)

`/internal/v1/identity/...` is only for other services. Each calling service has its own secret (at least 32 characters) and sends it as `X-Service-Token`. identity-service reads the secrets from `IDENTITY_INTERNAL_TOKEN_GATEWAY`, `_CART`, `_ORDER`, `_PAYMENT`, `_FINANCE`, `_NOTIFICATION` and `_PRODUCT`; with none set (the default, also in compose), every internal call returns 401. Each endpoint also only accepts the services the API spec lists, e.g. `/users/{id}/contact` only from notification-service.

```bash
cd identity-service
IDENTITY_INTERNAL_TOKEN_ORDER=dev-order-service-token-0123456789abcdef mvn spring-boot:run
curl -s -H 'X-Service-Token: dev-order-service-token-0123456789abcdef' \
  http://localhost:8083/internal/v1/identity/customers/<customerId>/checkout-eligibility
```

The gateway must never route `/internal/**`.

## Notification Service — in-app

Notification is one layered Spring Boot application: `controller → service → repository`, with `model`, `client`, `security` and `config` support packages. It does not send business email. Internal HTTP ingestion works without Kafka; the Kafka consumer is optional runtime configuration, not a second application.

For a host-run instance, set `NOTIFICATION_IDENTITY_URL=http://localhost:8083`, `NOTIFICATION_SERVICE_TOKEN` and the matching `IDENTITY_INTERNAL_TOKEN_NOTIFICATION` on Identity (at least 32 characters). Without a usable Identity credential, protected requests fail closed with 503. Compose supplies a dev-only matching token; never reuse it outside local development.

Authenticated in-app endpoints:

| Method | Path after `/api/v1` | Result |
|---|---|---|
| GET | `/notifications/me?page=1&size=20&unreadOnly=false` | Own latest 50 within 30 days; `data` and pagination `meta` |
| GET | `/notifications/me/unread-count` | Own visible unread count |
| POST | `/notifications/me/{notificationId}/read` | 204; other owner/missing/not visible is 404 |
| POST | `/notifications/me/read-all` | 204; visible own notifications only |
| DELETE | `/notifications/me/{notificationId}` | 204, including repeat delete; other owner is 404 |

Every request revalidates the Bearer token and `notification:read:own` through Identity. Gateway already routes `/api/v1/notifications/**`; do not expose internal paths through it. No `X-User-Id` supplied by a client grants ownership.

Producers call `POST /internal/v1/notifications` on the service port with their own `X-Service-Token`. Configure `NOTIFICATION_INTERNAL_TOKEN_IDENTITY`, `_PRODUCT`, `_ORDER`, `_PAYMENT`, `_FINANCE`; blank/short values disable that producer. Tokens must be distinct. Product's existing client uses `IDENTITY_INTERNAL_TOKEN_PRODUCT`, which Compose matches to Notification for local development.

Example product moderation notification (replace UUIDs with real recipient/product ids):

```bash
curl -i http://localhost:8084/internal/v1/notifications \
  -H "X-Service-Token: $NOTIFICATION_INTERNAL_TOKEN_PRODUCT" \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"product-removal-1","recipientUserId":"00000000-0000-4000-8000-000000000001","audienceContext":"SELLER","type":"PRODUCT_REMOVED","channels":["IN_APP"],"reference":{"type":"PRODUCT","id":"00000000-0000-4000-8000-000000000002","code":null},"templateData":{"productName":"Sản phẩm mẫu","reason":"Vi phạm"}}'
```

201 creates the notification; identical replay returns 200 with the original id, even after deletion. Changed payload with the same dedupe key returns 409. EMAIL/unknown template fields return 400; unauthorized producer/type is 401/403, unknown recipient is 404. No arbitrary message HTML, redirect URL or reset token is accepted. Full templates and required event snapshots are in [API §7.1.1/7.2](docs/requirements/api_specs.md).

Kafka: set `NOTIFICATION_KAFKA_ENABLED=true` and `KAFKA_BOOTSTRAP_SERVERS` only with a reachable broker and trusted producer ACLs. Topics match the event names in the API contract. Consumer group is `notification-service`, offsets commit after local writes; transient failures retry without dropping records or logging payloads. Invalid snapshots are recorded as `notification_inbox.state=FAILED_REVIEW` with a hash/error code, not their body. Fix producer data and republish with a new event id; an unchanged failed event may be replayed after fixing a dependency or implementation. Password-reset and seller verification events are not subscribed.

Root Compose still has no broker and Identity's outbox publisher remains unconfigured. Order/Finance producers are not implemented here. Consumer tests prove the Notification boundary, not an end-to-end checkout or delivery of Identity's pending outbox.

P-22 visibility is enforced on every read. The in-process retention worker removes expired/over-window content every `notification.retention.interval` (default `PT1H`, configurable maintenance interval, not an SLA), retaining ids, hashes and dedupe keys. `deleted_at` tombstones prevent replay from restoring content.

Isolated validation, no shared database or running stack required (Docker and Java 21):

```bash
cd notification-service
sh mvnw test
```

Tests start disposable PostgreSQL 16 and Kafka containers. HTTP tests call Notification on a random real port with an isolated Identity HTTP fixture; Identity's new recipient endpoint is checked separately by `InternalQueriesTests`.

## Events

identity-service records its events (`seller.approved`, `user.disabled`, ...) in the `identity.outbox_events` table. No message broker is set up yet, so nothing is sent: rows stay `PENDING` and identity logs "No message broker configured" at startup. To see what would be published:

```bash
docker exec ecommerce-database-1 psql -U ecommerce -d ecommerce \
  -c "select event_type, aggregate_id, version, status, created_at from identity.outbox_events order by created_at desc limit 20"
```

Other services verify tokens with the public key from `http://localhost:8083/.well-known/jwks.json` (`http://identity-service:8083/.well-known/jwks.json` inside Docker), issuer `ecommerce-identity`, audience `ecommerce-api`.

## Stopping

```bash
docker compose -p ecommerce down      # stop and remove containers, keep database data
docker compose -p ecommerce down -v   # also delete the postgres-data volume
```

identity-service's migration V6 (one account per role) stops with an error if the database still has users from the old model (one user with both a Customer and a Seller profile, an admin with a profile, several roles). Such dev data can't be converted automatically: wipe it with `docker compose -p ecommerce down -v` and start again. V6 also signs everyone out.
