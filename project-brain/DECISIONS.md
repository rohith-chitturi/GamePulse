# GamePulse — Architectural Decision Records (ADRs)

> **Documented Rationale for Engineering Decisions & Trade-offs**  
> **Rule**: Do not silently reverse an earlier decision. If circumstances require changing a decision, document the context and append a new record superseding the earlier one.

---

## ADR-001: Java 17 LTS and Spring Boot 3.3.5 Baseline
- **Date**: 2026-10-29
- **Status**: `ACCEPTED`
- **Context**: The project required a modern, production-grade Java enterprise stack with native support for Jakarta EE 10, modern HTTP servers, observability primitives (Micrometer, Actuator), and forward compatibility with Java 21+.
- **Options Considered**:
  1. *Java 11 + Spring Boot 2.7*: Stable but deprecated / End-of-Life.
  2. *Java 17 LTS + Spring Boot 3.3.5*: Fully supported LTS standard, Jakarta EE 10, modern observation engine.
  3. *Java 21 + Spring Boot 3.3.5*: Cutting edge, but requires matching CI runner tools and host Java availability.
- **Decision**: Adopt Java 17 LTS as the compilation target and Spring Boot 3.3.5 baseline.
- **Consequences**:
  - Consistent execution across developer Windows host (`Oracle JDK 17.0.12`) and GitHub Actions CI runner (`Temurin JDK 17`).
  - Seamless forward-compatibility with Java 21+ features when upgraded.
- **Evidence**: 100% build pass rate on local and CI runners.

---

## ADR-002: Modular Monolith vs. Distributed Microservices
- **Date**: 2026-10-29
- **Status**: `ACCEPTED`
- **Context**: Multiplayer services often prematurely split into multiple network-separated microservices before domain boundaries and transactional invariants are proven, causing distributed transaction complexity, network flakiness, and hard-to-reproduce test failures.
- **Options Considered**:
  1. *Multi-repo Microservices*: Separate services for Player, Matchmaking, Session, communicating over REST/gRPC.
  2. *Single-repo Modular Monolith*: Strict package boundaries (`com.gamepulse.player`, `.matchmaking`, `.session`, `.core`) sharing an authoritative database transaction manager.
- **Decision**: Implement GamePulse as a well-partitioned **modular monolith**.
- **Consequences**:
  - Invariants can be guaranteed through database transactions (`@Transactional`), row locks, and unique constraints without distributed 2PC or Saga overhead.
  - Zero network hops during initial concurrency verification.
  - Clear domain boundaries make future service decomposition straightforward if needed.
- **Evidence**: Verified end-to-end integration test passes in single in-process test runs without external network mocks.

---

## ADR-003: Deterministic FIFO Matchmaking with MMR Window Expansion
- **Date**: 2026-10-29
- **Status**: `ACCEPTED`
- **Context**: Matchmaking backends must balance skill parity (close MMR matches) with queue wait times without introducing speculative or non-deterministic behavior during automated testing.
- **Options Considered**:
  1. *Complex Heuristic / Graph Matching*: High algorithmic complexity, difficult to assert deterministic outcomes in unit and concurrency tests.
  2. *Deterministic FIFO with MMR Window Expansion*: Tickets are evaluated in strict queue order (`enqueuedAt ASC`). Tolerance window expands predictably: $\Delta_{\text{MMR}} = \text{baseTolerance} + (\text{waitSeconds} \times \text{expansionRate})$.
- **Decision**: Implement deterministic FIFO matchmaking with linear MMR expansion.
- **Consequences**:
  - Test suites can reliably predict match pairing outcomes based on simulated timestamps.
  - Candidate tickets are locked atomically in pairs, eliminating double-match hazards.
- **Evidence**: Unit test suite (`MatchmakingServiceTest`) reliably asserts match pairings and MMR window expansion.

---

## ADR-004: Standardizing Root Package Namespace to `com.gamepulse`
- **Date**: 2026-10-29
- **Status**: `ACCEPTED`
- **Context**: The repository initially used a vendor-specific namespace (`com.ea.gamepulse`) and employer-specific documentation headings. GamePulse is an independent portfolio project and must maintain neutral, independent open-source identity.
- **Options Considered**:
  1. *Leave legacy namespace unchanged*: Risky for portfolio presentations; implies official endorsement or internal project status.
  2. *Refactor to neutral `com.gamepulse`*: Clean open-source standard, aligns directory structure, POM coordinates, and logging hierarchies.
- **Decision**: Refactor all 43 Java classes, test files, directories, POM coordinates, and documentation to `com.gamepulse`.
- **Consequences**:
  - Removed all legacy publisher naming.
  - Established a permanent, independent engineering identity.
- **Evidence**: Merged in PR #3 (`7d987a7`), verified with zero remaining `com.ea` references across repository.

---

## ADR-005: Application-Level Idempotency Filter with SHA-256 Fingerprinting
- **Date**: 2026-10-29
- **Status**: `ACCEPTED`
- **Context**: Clients operating on flaky mobile or console networks frequently retry mutating HTTP operations (`POST`, `PUT`). If an operation succeeded but the client lost connection before receiving the response, retrying would create duplicate players, lobbies, or tickets.
- **Options Considered**:
  1. *Database Unique Constraints Only*: Protects against duplicates but returns a raw error (`500` or `409`) to the client rather than the successful original response.
  2. *Controller-Level Deduplication*: Inconsistent; must be manually wired into every single endpoint.
  3. *Servlet Filter with SHA-256 Body Fingerprint and Replay Cache*: Intercepts mutating requests carrying `Idempotency-Key` before controller dispatch. Caches response status, headers, and body.
- **Decision**: Implement `IdempotencyFilter` using a `CachedBodyHttpServletRequest` wrapper and dedicated `idempotency_records` table.
- **Consequences**:
  - Legitimate retries receive the original response with header `Idempotency-Replayed: true` without consuming business service or downstream database transaction CPU.
  - Re-using a key with a mismatched JSON payload is caught and rejected with `409 Conflict`.
  - Lease timeout (120s) ensures crashed requests can be safely reclaimed.
- **Evidence**: `IdempotencyIntegrationTest` asserts transparent replay and payload mismatch rejection.

---

## ADR-006: Pessimistic Row Locking (`PESSIMISTIC_WRITE`) for Capacity Boundaries
- **Date**: 2026-10-29
- **Status**: `ACCEPTED`
- **Context**: In multiplayer lobbies (e.g. 2-player or 4-player lobbies), high contention occurs when multiple players click "Join" on the final available slot simultaneously.
- **Options Considered**:
  1. *Optimistic Locking Only (`@Version`)*: Under heavy contention, all but one transaction fail with `OptimisticLockingFailureException`, requiring complex retry logic and degrading player experience.
  2. *Pessimistic Row Locking (`SELECT ... FOR UPDATE`)*: Serializes concurrent joins for that specific lobby record. Each transaction checks `players.size() < maxPlayers` with authoritative row locking.
- **Decision**: Use `PESSIMISTIC_WRITE` on `MatchSessionRepository.findByIdWithLock` paired with `@Version` as secondary defense.
- **Consequences**:
  - The first transaction fills the slot; subsequent queued transactions cleanly receive `409 Conflict (CAPACITY_EXCEEDED)` without database deadlock or unhandled exceptions.
  - Zero possibility of overfilling a lobby.
- **Evidence**: `ConcurrencyIntegrationTest.testParallelLobbyJoinsAtCapacityBoundary` released 10 simultaneous threads at a 1-slot boundary: exactly 1 succeeded, 9 received 409, final DB count strictly 2.

---

## ADR-007: Testcontainers Strategy with Local H2 Fallback
- **Date**: 2026-10-29
- **Status**: `ACCEPTED`
- **Context**: Real PostgreSQL integration tests should execute in continuous integration, but developers on Windows hosts often face Docker Desktop WSL2 named-pipe configuration nuances (`BadRequestException`).
- **Options Considered**:
  1. *Force Testcontainers for all tests*: Causes local build failures on developer workstations lacking running Docker daemons.
  2. *H2 only*: Fails to validate PostgreSQL-specific SQL, dialect features, and real database row locking.
  3. *Dual Strategy*: H2 in PostgreSQL compatibility mode for unit/fast integration tests; containerized PostgreSQL tests configured with `@Testcontainers(disabledWithoutDocker = true)`.
- **Decision**: Adopt the dual strategy.
- **Consequences**:
  - `mvn clean test` runs cleanly on any developer machine (Windows, macOS, Linux) without requiring Docker to be running.
  - Real PostgreSQL container tests run automatically in GitHub Actions Linux runners (`/var/run/docker.sock`).
- **Evidence**: Local test run executed 38 tests (36 passed, 2 skipped container tests); GitHub Actions CI executed all 38 tests with 100% pass rate in 1m 28s.

---

## ADR-008: Multi-Stage Production Containerization and Local Orchestration
- **Date**: 2026-11-04
- **Status**: `ACCEPTED`
- **Context**: The application required a reproducible, containerized runtime replicating production environments for local development, CI verification, and future deployment without requiring developers to manually configure PostgreSQL, Redis, Prometheus, or Grafana.
- **Options Considered**:
  1. *Single-stage fat Dockerfile*: Packages Maven and JDK into runtime image; results in large (~800MB) image with unnecessary build tool attack surface.
  2. *Multi-stage Dockerfile + Docker Compose*: Stage 1 uses `maven:3.9-eclipse-temurin-17-alpine` to compile jar with layer caching; Stage 2 uses minimal `eclipse-temurin:17-jre-alpine` (~180MB) running as non-root user `gamepulse:gamepulse`. Orchestrates 5 services (`gamepulse-app`, `postgres`, `redis`, `prometheus`, `grafana`) with health checks, named volumes, and internal network.
- **Decision**: Implement multi-stage `Dockerfile` and `docker-compose.yml` with configurable environment variables via `.env.example`.
- **Consequences**:
  - Minimal, secure production image footprint with no build tools in runtime image.
  - Hardened non-root execution (`gamepulse:gamepulse`).
  - Container-aware JVM memory configuration (`-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`).
  - Collision-proof host port mapping (`5433` for PostgreSQL, `6381` for Redis) avoids local development port conflicts while preserving standard internal DNS (`postgres:5432`, `redis:6379`).
  - Persistent volume storage across container recreations.
- **Evidence**:
  - `docker compose config` passed schema validation.
  - Stack started via `docker compose up -d --build`; all 5 containers transitioned to healthy.
  - REST player creation succeeded against containerized PostgreSQL; persisted cleanly across container recreation.
  - Prometheus target scraping reported state `UP`; Grafana reported health `ok`.


