# Running the app for development

## Prerequisites

- Docker with the Compose plugin
- Java 21, only needed to run or test a service outside Docker (the Docker build brings its own Maven). gateway-service and identity-service need a locally installed Maven 3.8+ (`mvn`); the other services ship a Maven wrapper that is not executable in git, so run it as `sh mvnw` (or use `mvn`).
- Free host ports: `5432` (Postgres), `8333` (object storage), `8761` (Eureka), `8080`–`8087` (services)

If a local PostgreSQL already uses 5432, set `POSTGRES_HOST_PORT` to publish the compose database on another host port, e.g. `POSTGRES_HOST_PORT=5433 ./up.sh`. This only changes the port used from the host; containers still reach the database at `database:5432`. Set it on **every** `docker compose` command (or `export` it once in your shell). Otherwise compose sees a changed port, recreates `database` on 5432, and the restart fails with "address already in use". The data volume survives this.

Always pass the same compose project name, `-p ecommerce`. `up.sh` builds images named `ecommerce-<service>`, which is what compose expects for that project name.

## Option 1 — Run everything in Docker

```bash
./up.sh
```

This builds an image for every `*-service/` folder, then starts the whole stack with `docker compose -p ecommerce up -d --no-build`. Startup order is enforced by health checks: `database` and `discovery-service` must be healthy before the other services start.

Check that it's up:

```bash
docker compose -p ecommerce ps
curl -s http://localhost:8083/actuator/health    # any service: /actuator/health
curl -s http://localhost:8081/actuator/health    # gateway: {"status":"UP"} only, no details
curl -s http://localhost:8083/hello              # Hello, World! from identity-service
```

Open the Eureka dashboard at http://localhost:8761 to see which services have registered.

| Service | URL |
|---|---|
| discovery-service (Eureka) | http://localhost:8761 |
| cart-service | http://localhost:8080 |
| gateway-service | http://localhost:8081 |
| finance-service | http://localhost:8082 |
| identity-service | http://localhost:8083 |
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

Running `./up.sh` again rebuilds and restarts everything.

### Logs

```bash
docker compose -p ecommerce logs -f identity-service
```

## Option 2 — Run a service locally, with infrastructure in Docker

Start only Postgres and Eureka:

```bash
docker compose -p ecommerce up -d database discovery-service object-storage
```

Then run the service from its folder:

```bash
cd identity-service
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ecommerce \
SPRING_DATASOURCE_USERNAME=ecommerce \
SPRING_DATASOURCE_PASSWORD=ecommerce \
mvn spring-boot:run
```

`application.properties` already points Eureka at `http://localhost:8761/eureka/`. identity-service also defaults to `jdbc:postgresql://localhost:5432/ecommerce`, so the datasource variables are only needed when the database is on another port (e.g. `POSTGRES_HOST_PORT=5433` → `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/ecommerce`). The other services don't use the database yet because JPA is still commented out in their `pom.xml`.

## Running tests

```bash
cd identity-service
mvn test
```

identity-service tests start their own throwaway PostgreSQL container (Testcontainers), so Docker must be running, but the compose stack doesn't need to be.

You can also skip Docker for Eureka entirely by running it with `sh mvnw spring-boot:run` inside `discovery-service/`.

Don't mix modes for services that call each other. A service started on the host registers in Eureka with the host's address, which containers such as the gateway may not be able to reach. Either run all the interacting services on the host, or run them all in Docker.

## Database

| | |
|---|---|
| From the host | `jdbc:postgresql://localhost:5432/ecommerce` (or your `POSTGRES_HOST_PORT`) |
| From containers | `jdbc:postgresql://database:5432/ecommerce` |
| User / password | `ecommerce` / `ecommerce` (dev only) |
| identity-service tables | schema `identity` (created by Flyway on startup) |

```bash
docker compose -p ecommerce exec database psql -U ecommerce -d ecommerce
```

The data lives in the `postgres-data` volume.

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

Password reset emails link to `IDENTITY_PASSWORD_RESET_LINK_BASE` (default `http://localhost:3000/reset-password`) with `?token=...` appended; set it to the FE's reset page.

Quick check of the auth flow against identity-service directly (with the stack in Docker, the same paths also work through the gateway: `B=http://localhost:8081/api/v1/identity/auth`):

```bash
B=http://localhost:8083/api/v1/identity/auth
curl -s -H 'Content-Type: application/json' -d '{"email":"an@example.test","fullName":"An","phoneNumber":"0912345678","password":"Secret123","confirmPassword":"Secret123"}' $B/register
curl -s -H 'Content-Type: application/json' -d '{"email":"an@example.test","password":"Secret123","accountType":"CUSTOMER"}' $B/login
```

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
