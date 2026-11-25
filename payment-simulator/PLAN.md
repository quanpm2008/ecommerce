# Execution Plan & Checklist: Payment Provider Simulator

Date: 2026-11-25

## Status

In Progress (awaiting final test verification)

## Outcome

Deploy a standalone external Payment Provider Simulator (`payment-simulator`) outside `payment-service` Spring Boot application, running as a separate JVM process inside the `payment-service` container on loopback HTTP (`127.0.0.1:8089`, published `8089:8089`), implementing the full `DEMO_PROVIDER` HTTP + customer `/pay/{token}` + signed HMAC-SHA256 webhook lifecycle with atomic JSON snapshot persistence.

## Context

- Authority: `docs/requirements/payments.md`, `docs/engineering.md`, `payment-service` `ProviderContracts.java`, `ProviderHttpGateway.java`, `ProviderWebhookController.java`, and user-approved sequence diagram (25/11/2026).
- `ProviderHttpGateway` enforces HTTPS or loopback HTTP (`127.0.0.1` / `localhost`). Co-locating `payment-simulator` in the same container as `payment-service` allows `http://127.0.0.1:8089` without TLS while keeping process, classpath, and storage completely isolated.
- DB ownership rule (`docs/engineering.md`): `payment-simulator` must NOT touch PostgreSQL schema `payment_service`. State is stored in-memory with atomic JSON snapshot persistence (`SIMULATOR_STATE_FILE=/tmp/payment-simulator-state.json`).

## Architecture & Scope

- **Standalone module (`payment-simulator/`, sibling of `payment-service/`):**
  - `SimulatorConfig`: loads `SIMULATOR_PORT` (8089), `SIMULATOR_PUBLIC_ORIGIN` (`http://localhost:8089`), `SIMULATOR_CREDENTIAL_HEADER` (`X-Provider-Key`), `SIMULATOR_CREDENTIAL`, `SIMULATOR_WEBHOOK_URL` (`http://127.0.0.1:8086/api/v1/payments/webhooks/demo-provider`), `SIMULATOR_WEBHOOK_SECRET`, `SIMULATOR_STATE_FILE` (`/tmp/payment-simulator-state.json`).
  - `JsonStateStore`: thread-safe store + atomic temp-file-and-move JSON snapshot persistence.
  - `WebhookDispatcher`: async HMAC-SHA256 (`X-Webhook-Timestamp`, `X-Webhook-Signature`) webhook delivery with retry backoff until `204`/`200`/`409`.
  - `PaymentPageRenderer`: HTML5 UI for `/pay/{token}` with simulation badge (`Giả lập — không phát sinh tiền thật`) and Confirm (`SUCCESS`) / Decline (`FAILED`) actions.
  - `SimulatorServer`: HTTP endpoints for Provider API, Customer UI, and `/__sim/*` scenario controls.
- **Container & Compose wiring:**
  - `payment-service/entrypoint.sh`: starts `payment-simulator.jar` in background and `mvn -B spring-boot:run` in foreground with trap cleanup.
  - `payment-service/Dockerfile`: builds from repo root context (`.`), packages `payment-simulator`, copies `entrypoint.sh`, exposes `8086` and `8089`.
  - `compose.yaml` & `up.sh`: sets `payment-service` build context to `.`, configures `PAYMENT_PROVIDER_*` + `SIMULATOR_*` env vars and publishes `8089:8089`.

## Checklist

### 1. Standalone Simulator Implementation (`payment-simulator/`)
- [x] `pom.xml` with Java 21, Jackson (`databind` + `jsr310`), JUnit 5, `maven-shade-plugin`
- [x] `POST /v1/payments`: credential check (`401`), validation (`400`), expiry check (`409`), idempotency by `paymentId` (`200`/`409`), fault injection (`REJECT`, `DROP_AFTER_COMMIT`, `DELAY_AFTER_COMMIT`, `LATE_SUCCESS`)
- [x] `GET /v1/payments/{paymentId}`: status query, auto-expiration (`LINK_EXPIRED`) + webhook dispatch
- [x] `POST /v1/refunds`: credential check, payment `SUCCESS` & `providerTransactionId` check, obligation & over-refund check (`409`), idempotency by `(refundId, attemptId)`, reject `409` if attempt already closed by `GET`, fault injection (`REJECT`, `PENDING`, `LATE_SUCCESS`, `DROP_AFTER_COMMIT`, `DELAY_AFTER_COMMIT`)
- [x] `GET /v1/refunds/{refundId}/attempts/{attemptId}`: attempt lookup (`200`) or persistent closed-missing tombstone (`404` `REFUND_ATTEMPT_NOT_FOUND` with stable `providerRefundId` and `completedAt`)
- [x] `GET /pay/{token}` & `POST /pay/{token}/confirm`: HTML page + single-capture confirmation (`SUCCESS` / `FAILED`) + signed `payment.result` webhook + configurable frontend return link (`SIMULATOR_FRONTEND_RETURN_URL`)
- [x] `/__sim/*` control endpoints: `/__sim/health`, `/__sim/state`, `/__sim/scenario`, `/__sim/payments/{paymentId}/complete`, `/__sim/refunds/{refundId}/attempts/{attemptId}/complete`
- [x] Atomic JSON snapshot persistence (`JsonStateStore`) surviving process restart

### 2. Container & Runtime Wiring
- [x] `payment-service/entrypoint.sh` launching simulator + Spring Boot service
- [x] `payment-service/Dockerfile` building shaded jar from root context and exposing `8086`, `8089`
- [x] `compose.yaml` & `up.sh` wiring `PAYMENT_PROVIDER_*`, `PAYMENT_REFUND_QUERY_INTERVAL`, `SIMULATOR_*`, port `8089:8089`, and root build context

### 3. Validation
- [x] `mvn -B test package -f payment-simulator/pom.xml` (`SimulatorServerTests`: 2/2 passed)
- [x] `mvn -B -Dtest=ProviderHttpContractTests test -f payment-service/pom.xml` (`ProviderHttpContractTests`: 6/6 passed)
- [ ] Docker build & compose smoke test (`docker compose -p ecommerce up -d --build payment-service` — requires `.env` with `DB_JDBC_URL`)

## Risks And Recovery

- `payment-service` container mounts `./payment-service/src:/app/src:ro`; simulator jar lives at `/app/simulator/target/payment-simulator.jar` inside the image so source volume mount does not shadow it.
- Restarting `payment-service` container preserves `/tmp/payment-simulator-state.json`; recreating the container resets simulator state cleanly.
