# Execution Plan: Notification Service in-app

Date: 2026-11-25

## Status

Completed

## Outcome

Implement Notification's required in-app APIs and trusted event/internal ingestion with PostgreSQL persistence, recipient isolation, durable dedupe, and P-22 retention. Business email remains deferred.

## Context

Authority: docs/requirements/notifications.md, API §6.7/7.1.1/7.2, Database §3.7, docs/engineering.md, coverage.md#notification-channels. User approved enriching source events with recipient/display snapshots on 25/11/2026 rather than introducing Order/Payment lookup dependencies.

## Scope

- Notification persistence/migration, creation/templates, public and internal APIs, Identity authorization and recipient existence, Kafka consumer.
- Existing producer integration where available; no implementation of Order/Finance business workflows or business email.
- Isolated PostgreSQL/Kafka and HTTP proof; small commits after verification.

## Approach

1. Establish shared Java records and API/store boundaries.
2. Implement transactional persistence/dedupe and owner-scoped query/read/delete/retention; implement HTTP authorization/internal caller allowlist independently.
3. Map events to recipient-specific safe templates; wire Kafka without logging payloads or dropping failed records.
4. Exercise real PostgreSQL, Kafka and HTTP boundaries, update requirements/runtime guidance and commit by slice.

## Risks And Recovery

- Other services remain incomplete: event fixtures prove consumer contracts, not full checkout runtime.
- Auth email events must never create in-app notifications or enter ordinary logs/storage.
- New migration is additive in notification schema; do not touch existing databases while testing.
- Replay after deletion/expiry retains dedupe tombstones; consumer commits offsets only after local transaction.

## Progress

- [x] Read docs/current service patterns and obtain event snapshot decision.
- [x] Implement persistence and in-app APIs.
- [x] Implement event mapping and integration.
- [x] Verify; deliver changes as small logical commits.

## Decisions

- 2026-11-25: User chose enriched source events for missing recipient/display data.
- 2026-11-25: User explicitly required Layered Architecture. Notification uses controller/service/repository/model packages in one Spring Boot module/process/schema; no CQRS or independent delivery deployment.
- Reuse service-local MVC/Feign and X-Service-Token conventions; no shared business library.

## Validation

- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 sh mvnw clean test` in notification-service: PASS, 15 tests (9 persistence, 5 actual HTTP endpoints, 1 real Kafka).
- After adding transient retry/privacy proof, `sh mvnw -Dtest=NotificationKafkaTests test`: PASS, 2 Kafka tests. Total distinct Notification behavior tests: 16.
- Identity `mvn -Dtest=InternalQueriesTests,OpenApiDocsTests test`: PASS, 9 tests; regenerated OpenAPI artifacts.
- Positive/negative proof covers owner isolation, missing/revoked/disabled/no-permission sessions, producer allowlist, secret template rejection, recipient validation, concurrent dedupe, replay tombstones, retention windows, multi-seller fanout, refund cycle dedupe, BANNED messaging, Kafka recovery without payload logging.
- All infrastructure was Testcontainers-owned; HTTP source dependency was an isolated Identity fixture, not a claim about end-to-end checkout. Initial compilation and Feign initialization failures were corrected before successful checks.

## Result

Implemented required in-app Notification with Layered Architecture, durable PostgreSQL storage, trusted HTTP/event ingestion, current Identity authorization, P-22 read/delete/retention and safe retry. Email business delivery remains intentionally deferred. Root Compose credentials align Notification with Identity and Product; no shared runtime was restarted or migrated. Root Compose has no Kafka, Identity publisher remains unconfigured and Order/Finance producers remain outside this task, so no claim of full checkout/Identity-outbox delivery. Local proof passed; CI, hooks and branch protection were not changed or verified.
