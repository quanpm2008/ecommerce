# Running identity-service

identity-service is the "Identity & Seller" service of the e-commerce MVP: accounts, sign-in (JWT), customer and seller profiles, addresses, admin review and bans. This guide runs it on its own with Docker, either from the published image or built from this source.

You get three containers from [`compose.yaml`](compose.yaml) in this folder:

| Container | What it is | Reachable at |
|---|---|---|
| `identity-service` | the service | http://localhost:8083 |
| `database` | PostgreSQL 16 (data in volume `identity_identity-postgres-data`) | inside Docker only |
| `object-storage` | SeaweedFS, S3-compatible storage for seller logos | http://localhost:8333 |

Eureka and the other services are not needed.

## Requirements

- Docker with Compose v2 (`docker compose version`). Docker Desktop on Windows/macOS works. The image runs on Intel/AMD (`linux/amd64`) and Apple Silicon/ARM (`linux/arm64`).
- Free ports 8083 and 8333 (change them below if not).
- Internet access for the container: provinces and wards come from `https://provinces.open-api.vn`.

## Option A: run the published image

Image: [`hnan756/identity-service`](https://hub.docker.com/r/hnan756/identity-service), tags `latest` and dated ones such as `20261031`.

1. Get this folder's `compose.yaml` and `infra/seaweedfs/s3.json` (the storage credentials it mounts). Either clone the repository:

   ```bash
   git clone https://github.com/quanpm2008/ecommerce.git
   cd ecommerce/identity-service
   ```

   or copy just those two files into a folder, keeping the same layout (`compose.yaml` next to `infra/seaweedfs/s3.json`).

2. Start it:

   ```bash
   docker compose pull
   docker compose up -d
   docker compose ps          # identity-service becomes "healthy" after ~30 s
   ```

To use a fixed tag instead of `latest`: `IDENTITY_IMAGE=hnan756/identity-service:20261031 docker compose up -d`.

## Option B: build from the local source

From this folder (`identity-service/`), with the source checked out:

```bash
docker compose up -d --build
```

This builds the image from [`Dockerfile`](Dockerfile) (Maven runs inside Docker; no local Java or Maven needed) and tags it locally as `hnan756/identity-service:latest`, so a later `docker compose up -d` keeps using your build until you `docker compose pull` again.

To work on the code without Docker for the service itself, see [`../DEVELOPMENT.md`](../DEVELOPMENT.md) (Java 21 and Maven 3.8+; `mvn test` needs Docker for Testcontainers).

## Check that it works

```bash
curl http://localhost:8083/actuator/health                 # {"status":"UP"}
curl http://localhost:8083/api/v1/identity/regions/provinces
```

Then open the API docs:

- http://localhost:8083/swagger-ui.html: every endpoint, try requests in the browser.
- [`api.html`](api.html) and [`openapi.json`](openapi.json) in this folder: offline docs and the spec for **Postman → Import → File** (creates a collection with `baseUrl` = `http://localhost:8083`).

A first round trip:

```bash
B=http://localhost:8083/api/v1/identity
curl -s -H 'Content-Type: application/json' \
  -d '{"email":"an@example.test","fullName":"Nguyen Van An","phoneNumber":"0912345678","password":"Secret123","confirmPassword":"Secret123"}' \
  $B/auth/register
curl -s -H 'Content-Type: application/json' -d '{"email":"an@example.test","password":"Secret123"}' $B/auth/login
# copy data.accessToken, then:
curl -s -H "Authorization: Bearer <accessToken>" $B/users/me
```

Passwords need at least 8 characters with an uppercase letter, a lowercase letter and a digit; phone numbers are Vietnamese mobiles (`0xxxxxxxxx`).

## Settings

Set these in your shell (or a `.env` file next to `compose.yaml`) before `docker compose up -d`. All are optional.

| Variable | Default | Purpose |
|---|---|---|
| `IDENTITY_HOST_PORT` | `8083` | host port of the service |
| `S3_HOST_PORT` | `8333` | host port of the storage (also used in upload URLs) |
| `IDENTITY_IMAGE` | `hnan756/identity-service:latest` | image to run |
| `IDENTITY_BOOTSTRAP_ADMIN_EMAIL` / `IDENTITY_BOOTSTRAP_ADMIN_PASSWORD` | not set | creates an admin at startup if no user has that email (there is no public way to create admins) |
| `IDENTITY_TOKEN_SIGNING_KEY` | not set | RSA private key (PKCS#8 PEM) for signing tokens. Without it a temporary key is generated, so **every restart signs everyone out** |
| `IDENTITY_PASSWORD_RESET_LINK_BASE` | `http://localhost:3000/reset-password` | front-end page that reset emails link to |
| `GEO_API_BASE_URL` | `https://provinces.open-api.vn/api/v2` | region source; empty turns regions off (address writes then return 503) |
| `IDENTITY_ORDER_BASE_URL` | `http://order-service:8085` | order-service, asked when banning a customer; without it such bans return 503 |
| `IDENTITY_INTERNAL_TOKEN_<SERVICE>` (`GATEWAY`, `CART`, `ORDER`, `PAYMENT`, `FINANCE`, `NOTIFICATION`, `PRODUCT`) | not set | `X-Service-Token` each service uses for `/internal/v1/identity` (at least 32 characters); none set = every internal call is refused |

Example with an admin and a fixed signing key:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out signing-key.pem
export IDENTITY_TOKEN_SIGNING_KEY="$(cat signing-key.pem)"
export IDENTITY_BOOTSTRAP_ADMIN_EMAIL=admin@example.test
export IDENTITY_BOOTSTRAP_ADMIN_PASSWORD='Admin1234'
docker compose up -d
```

## What doesn't work on its own

- No message broker is set up, so events (`seller.approved`, `user.disabled`, ...) are stored in `identity.outbox_events` but not sent anywhere.
- Banning with scope `CUSTOMER` needs order-service and returns 503 without it. Scopes `USER` and `SELLER` work.
- The gateway is not part of this setup; call the service directly on port 8083.

## Logs, stop, reset

```bash
docker compose logs -f identity-service
docker compose down         # stop; keeps the database and uploaded files
docker compose down -v      # stop and delete all data
```

## Publishing a new image (maintainers)

Logged in as the Docker Hub owner (`docker login`), from this folder:

```bash
docker buildx build --platform linux/amd64,linux/arm64 \
  -t hnan756/identity-service:latest -t hnan756/identity-service:$(date +%Y%m%d) --push .
```

The Dockerfile is the team's dev image: a single Maven stage that runs `mvn spring-boot:run`, so the whole image, Maven included, is built per platform and the non-native one (usually `linux/arm64`) goes through QEMU emulation and is slow. This needs a builder that can export multi-platform images (Docker Desktop, or Docker Engine with the containerd image store) and has QEMU registered (`docker run --privileged --rm tonistiigi/binfmt --install arm64` if not). On the dev machine (rootless Docker), add `--network=host`.
