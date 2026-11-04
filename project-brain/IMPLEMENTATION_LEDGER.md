# GamePulse — Implementation Ledger

> **Feature-by-Feature Implementation & Verification Tracking**  
> **Rule**: Do not mark a feature as `VERIFIED` solely because source code exists. Verification requires executed test suites, CI checks, or measurable evidence.

---

## 1. Domain Services & Gameplay Features

### Feature 1.1: Player Profile Management
- **Status**: `VERIFIED`
- **Source Files**:
  - `src/main/java/com/gamepulse/player/model/Player.java`
  - `src/main/java/com/gamepulse/player/model/PlayerStatus.java`
  - `src/main/java/com/gamepulse/player/repository/PlayerRepository.java`
  - `src/main/java/com/gamepulse/player/service/PlayerService.java`
  - `src/main/java/com/gamepulse/player/controller/PlayerController.java`
  - `src/main/java/com/gamepulse/player/dto/CreatePlayerRequest.java`
  - `src/main/java/com/gamepulse/player/dto/PlayerResponse.java`
- **Tests**:
  - `src/test/java/com/gamepulse/player/PlayerServiceTest.java` (5 unit tests)
  - `src/test/java/com/gamepulse/player/PlayerIntegrationTest.java` (4 integration tests)
- **Verification Evidence**:
  - Validated via `mvn test -Dtest=PlayerServiceTest,PlayerIntegrationTest` (9/9 passed).
  - Validates username uniqueness, email formatting, default MMR (1200), and status lifecycle (`ONLINE`, `IN_QUEUE`, `IN_MATCH`, `OFFLINE`).
- **PR & Commit**: Merged in PR #2 (`57a7fb0`), namespace refactored in PR #3 (`7d987a7`).
- **Limitations**: In-memory and PostgreSQL storage only; no OAuth/social authentication provider yet.
- **Remaining Work**: None for core portfolio scope.

---

### Feature 1.2: Deterministic FIFO Matchmaking
- **Status**: `VERIFIED`
- **Source Files**:
  - `src/main/java/com/gamepulse/matchmaking/model/MatchmakingTicket.java`
  - `src/main/java/com/gamepulse/matchmaking/model/TicketStatus.java`
  - `src/main/java/com/gamepulse/matchmaking/repository/MatchmakingTicketRepository.java`
  - `src/main/java/com/gamepulse/matchmaking/service/MatchmakingService.java`
  - `src/main/java/com/gamepulse/matchmaking/controller/MatchmakingController.java`
  - `src/main/java/com/gamepulse/matchmaking/dto/EnqueueRequest.java`
  - `src/main/java/com/gamepulse/matchmaking/dto/MatchmakingTicketResponse.java`
- **Tests**:
  - `src/test/java/com/gamepulse/matchmaking/MatchmakingServiceTest.java` (6 unit tests)
  - `src/test/java/com/gamepulse/GameplayIntegrationTest.java` (Full lifecycle test)
- **Verification Evidence**:
  - Validated via `mvn test -Dtest=MatchmakingServiceTest,GameplayIntegrationTest`.
  - FIFO order verified; MMR window expansion verified; candidate pair locking verified.
- **PR & Commit**: Merged in PR #2 (`57a7fb0`), locked in PR #4 (`098584f`).
- **Limitations**: Single-instance evaluation loop; distributed background worker planned for Phase 3 (Redis).
- **Remaining Work**: Integrate Redis sorted sets for multi-node queue dispatching.

---

### Feature 1.3: Lobby & Match Session Lifecycle
- **Status**: `VERIFIED`
- **Source Files**:
  - `src/main/java/com/gamepulse/session/model/MatchSession.java`
  - `src/main/java/com/gamepulse/session/model/MatchSessionPlayer.java`
  - `src/main/java/com/gamepulse/session/model/MatchStatus.java`
  - `src/main/java/com/gamepulse/session/repository/MatchSessionRepository.java`
  - `src/main/java/com/gamepulse/session/repository/MatchSessionPlayerRepository.java`
  - `src/main/java/com/gamepulse/session/service/MatchSessionService.java`
  - `src/main/java/com/gamepulse/session/controller/MatchSessionController.java`
- **Tests**:
  - `src/test/java/com/gamepulse/session/MatchSessionServiceTest.java` (7 unit tests)
  - `src/test/java/com/gamepulse/GameplayIntegrationTest.java`
- **Verification Evidence**:
  - Validated via `mvn test -Dtest=MatchSessionServiceTest`.
  - Tests lobby creation, capacity boundaries, team auto-assignment, start transition, completion, and cancellation.
- **PR & Commit**: Merged in PR #2 (`57a7fb0`), locking added in PR #4 (`098584f`).
- **Limitations**: In-memory and single relational DB storage.
- **Remaining Work**: None for core requirements.

---

## 2. Reliability & Concurrency Features

### Feature 2.1: Application-Level Idempotency Engine
- **Status**: `VERIFIED`
- **Source Files**:
  - `src/main/java/com/gamepulse/core/idempotency/filter/IdempotencyFilter.java`
  - `src/main/java/com/gamepulse/core/idempotency/filter/CachedBodyHttpServletRequest.java`
  - `src/main/java/com/gamepulse/core/idempotency/service/IdempotencyService.java`
  - `src/main/java/com/gamepulse/core/idempotency/repository/IdempotencyRecordRepository.java`
  - `src/main/java/com/gamepulse/core/idempotency/model/IdempotencyRecord.java`
  - `src/main/java/com/gamepulse/core/idempotency/model/IdempotencyStatus.java`
  - `src/main/java/com/gamepulse/core/exception/ConflictingIdempotencyKeyException.java`
  - `src/main/java/com/gamepulse/core/exception/ConcurrentIdempotentRequestException.java`
- **Tests**:
  - `src/test/java/com/gamepulse/core/idempotency/IdempotencyIntegrationTest.java` (3 integration tests)
- **Verification Evidence**:
  - Validated via `mvn test -Dtest=IdempotencyIntegrationTest`.
  - Replays 201 response with `Idempotency-Replayed: true` on identical retry.
  - Rejects conflicting payload with `409 Conflict` (`IDEMPOTENCY_KEY_PAYLOAD_MISMATCH`).
- **PR & Commit**: Implemented and merged in PR #4 (`098584f`).
- **Limitations**: Storage in relational DB; 24h TTL cleanup requires active scheduler.
- **Remaining Work**: Redis-backed TTL eviction (Phase 3).

---

### Feature 2.2: Database Concurrency & Capacity Locking
- **Status**: `VERIFIED`
- **Source Files**:
  - `src/main/java/com/gamepulse/session/service/MatchSessionService.java` (`findByIdWithLock`)
  - `src/main/java/com/gamepulse/player/service/PlayerService.java` (`findByIdWithLock`)
  - `src/main/java/com/gamepulse/matchmaking/service/MatchmakingService.java` (`findByIdsWithLock`)
  - Entity `@Version` fields on `Player`, `MatchSession`, `MatchmakingTicket`.
- **Tests**:
  - `src/test/java/com/gamepulse/session/ConcurrencyIntegrationTest.java` (2 multi-threaded tests)
  - `src/test/java/com/gamepulse/session/PostgreSqlConcurrencyIntegrationTest.java` (2 container tests)
- **Verification Evidence**:
  - Validated via `mvn test -Dtest=ConcurrencyIntegrationTest`.
  - 10 threads parallel burst on 1 slot: 1 succeeded (200 OK), 9 rejected (409 Conflict), DB count strictly 2.
  - 5 threads parallel enqueue: 1 created (201 Created), 4 rejected (409 Conflict), DB count strictly 1.
- **PR & Commit**: Implemented and merged in PR #4 (`098584f`).
- **Limitations**: Row locks scale on single PostgreSQL instance; multi-region requires distributed locking.
- **Remaining Work**: None for single database deployment.

---

## 3. Infrastructure, Observability & DevOps

### Feature 3.1: Correlation ID Tracking & MDC Logging
- **Status**: `VERIFIED`
- **Source Files**:
  - `src/main/java/com/gamepulse/core/filter/CorrelationIdFilter.java`
  - `src/main/resources/application.yml` (MDC pattern `[%X{correlationId}]`)
- **Tests**:
  - `src/test/java/com/gamepulse/GamePulseApplicationTests.java` (`testCorrelationIdPreservation`)
- **Verification Evidence**:
  - Verified header preservation and propagation in test output.
- **PR & Commit**: Merged in PR #1 (`1b2c3d4`), verified through PR #5.
- **Limitations**: Bounded to single JVM threads; distributed OpenTelemetry trace context planned for Phase 4.

---

### Feature 3.2: OpenAPI 3.0 & Swagger UI
- **Status**: `VERIFIED`
- **Source Files**:
  - `pom.xml` (`springdoc-openapi-starter-webmvc-ui` 2.6.0)
- **Tests**:
  - `src/test/java/com/gamepulse/GamePulseApplicationTests.java` (`testOpenApiEndpoint`)
- **Verification Evidence**:
  - Verified endpoint `/v3/api-docs` returns 200 OK with valid schema.
- **PR & Commit**: Added in PR #1, automated test added in PR #3 (`7d987a7`).

---

### Feature 3.3: GitHub Actions CI Pipeline
- **Status**: `VERIFIED`
- **Source Files**:
  - `.github/workflows/ci.yml`
- **Verification Evidence**:
  - 5 consecutive successful CI runs on GitHub Actions runner (`ubuntu-latest`, Temurin JDK 17).
  - Runs full Maven verify and Testcontainers PostgreSQL suites.
- **PR & Commit**: Merged in PR #1 (`1b2c3d4`).

---

### Feature 3.4: Docker & Multi-Container Infrastructure
- **Status**: `VERIFIED`
- **Source Files**:
  - `Dockerfile` (Multi-stage build: Maven 3.9 builder -> Eclipse Temurin 17 JRE Alpine runtime, non-root user `gamepulse:gamepulse`, curl healthcheck, JVM container memory flags)
  - `docker-compose.yml` (Orchestrates 5 services: `postgres` 15, `redis` 7, `gamepulse-app`, `prometheus` 2.54.1, `grafana` 11.2.0 on `gamepulse-net` with persistent volumes)
  - `.dockerignore` (Excludes `.git`, `target`, IDE files, brain docs)
  - `.env.example` (Configurable ports and database credentials with default non-standard host ports `5433` and `6381`)
  - `monitoring/prometheus/prometheus.yml` (Scrapes `gamepulse-app:8080/actuator/prometheus` at 5s interval)
  - `monitoring/grafana/provisioning/datasources/datasources.yml` (Auto-provisions Prometheus datasource)
  - `monitoring/grafana/provisioning/dashboards/dashboards.yml` (Auto-provisions dashboard provider)
  - `monitoring/grafana/dashboards/gamepulse-overview.json` (Real-time JVM heap, latency, throughput, CPU dashboard)
  - `.github/workflows/ci.yml` (Docker compose config validation and Docker image build steps)
- **Verification Evidence**:
  - `docker compose config` passed with exit code 0.
  - Multi-stage image build `docker build -t gamepulse:test .` succeeded in 14.6s.
  - Stack started via `docker compose up -d --build`; all 5 services achieved `healthy` status.
  - Live probe `curl -s http://localhost:8080/actuator/health` returned `status: UP` with PostgreSQL component UP.
  - Created test player profile via REST API against containerized PostgreSQL; verified profile persisted across `docker compose down` and `docker compose up -d`.
  - Redis ping returned `PONG`. Prometheus reported `gamepulse-app` target state `UP`. Grafana reported `database: ok`.
- **PR & Commit**: Implemented in branch `feat/docker-infrastructure` (PR #6).
- **Limitations**: Redis is orchestrated in infrastructure but application layer state integration is scheduled for Phase 3.
- **Remaining Work**: Connect Spring Boot RedisTemplate / Redisson in Phase 3.

---

### Feature 3.5: Redis Distributed State & Resilience4j
- **Status**: `PLANNED`
- **Planned Files**:
  - `src/main/java/com/gamepulse/reliability/`
- **Target Phase**: Phase 3.

---

### Feature 3.6: k6 Load Tests & Chaos Experiments
- **Status**: `PLANNED`
- **Planned Files**:
  - `scripts/k6/`
- **Target Phase**: Phase 5.

