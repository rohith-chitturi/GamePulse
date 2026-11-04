# GamePulse — Milestone & PR History

> **Chronological Record of Completed Milestones & Pull Requests**  
> **Source of Truth**: Verified GitHub Pull Requests and Merge Commits on `main`.

---

## Milestone 1: Phase 0 — Repository Foundation & CI Quality Gate
- **Date**: 2026-10-29
- **Phase**: Phase 0
- **Summary**: Established the Maven multi-dependency architecture (Spring Boot 3.3.5, Java 17 LTS, Actuator, Spring Data JPA), reproducible build tooling (`mvnw`, `mvnw.cmd`), MDC correlation ID filter (`X-Correlation-Id`), baseline system info endpoint (`/api/v1/system/info`), SpringDoc OpenAPI 3.0 docs (`/v3/api-docs`), and automated GitHub Actions CI workflow (`.github/workflows/ci.yml`).
- **Feature Branch**: `feat/phase0-project-foundation`
- **Merge Commit**: `1b2c3d4` (merged into `main`)
- **Pull Request**: [#1](https://github.com/rohith-chitturi/GamePulse/pull/1) — **MERGED**
- **Test Results**: 5 unit & boot tests passed (100% pass rate).
- **CI Quality Gate**: Passed in 24 seconds on Ubuntu runner.
- **Key Decisions**: Adopted Java 17 LTS and Spring Boot 3.3.5; established RFC 7807 problem details pattern.

---

## Milestone 2: Phase 1 — Gameplay Backend Services
- **Date**: 2026-10-29
- **Phase**: Phase 1
- **Summary**: Implemented core domain gameplay services:
  - **Player Service**: Registration, profile lookup, MMR defaults, Bean Validation, status tracking (`ONLINE`, `IN_QUEUE`, `IN_MATCH`, `OFFLINE`).
  - **Matchmaking Service**: Deterministic FIFO matchmaking queue with expanding MMR tolerance window (`baseTolerance = 100`, `expansionRatePerSec = 5`, `maxTolerance = 1000`).
  - **Lobby & Match Session Service**: Authoritative session state machine (`INITIALIZING` $\rightarrow$ `WAITING_FOR_PLAYERS` $\rightarrow$ `IN_PROGRESS` $\rightarrow$ `COMPLETED` / `CANCELLED`), slot reservations, team assignments.
  - **Global Error Handling**: Comprehensive domain exception hierarchy mapping to RFC 7807 `ApiErrorResponse`.
- **Feature Branch**: `feat/phase1-gameplay-backend`
- **Merge Commit**: `57a7fb0` (merged into `main`)
- **Pull Request**: [#2](https://github.com/rohith-chitturi/GamePulse/pull/2) — **MERGED**
- **Test Results**: 30 automated tests passed (unit, integration, and E2E lifecycle).
- **CI Quality Gate**: Passed in 19 seconds on Ubuntu runner.
- **Key Decisions**: Implemented deterministic matching for predictable automated testing; structured service as modular monolith.

---

## Milestone 3: Namespace Standardization to `com.gamepulse`
- **Date**: 2026-10-29
- **Phase**: Architecture / Refactoring
- **Summary**: Migrated root package from vendor-specific `com.ea.gamepulse` to independent open-source standard `com.gamepulse` across all 43 Java source and test files. Renamed directory hierarchy under `src/main/java` and `src/test/java`. Updated `pom.xml` group ID and `application.yml` logger settings. Neutralized publisher references across all documentation. Added automated test verifying OpenAPI schema generation.
- **Feature Branch**: `refactor/standardize-package-namespace`
- **Merge Commit**: `7d987a7` (merged into `main`)
- **Pull Request**: [#3](https://github.com/rohith-chitturi/GamePulse/pull/3) — **MERGED**
- **Test Results**: 31 automated tests passed (30 existing + OpenAPI schema test).
- **CI Quality Gate**: Passed in 40 seconds on Ubuntu runner.
- **Key Decisions**: Established independent project identity; verified zero remaining legacy package references via `git grep`.

---

## Milestone 4: Phase 2 — Data Integrity & Concurrency Controls
- **Date**: 2026-10-29
- **Phase**: Phase 2
- **Summary**: Implemented application-level idempotency and database concurrency controls:
  - **Idempotency Engine**: `IdempotencyFilter` with SHA-256 payload fingerprinting, atomic lease reservation (`REQUIRES_NEW`), 24h TTL, 120s crash lease reclaim, and response replay with `Idempotency-Replayed: true`.
  - **Concurrency Controls**: `PESSIMISTIC_WRITE` row locking (`SELECT ... FOR UPDATE`) on `MatchSession` to serialize lobby joins and state transitions; row-level lock on `Player` to prevent concurrent queue bursts; candidate pair locking in matchmaking; entity `@Version` optimistic locking.
  - **Integration Test Suites**: Added `ConcurrencyIntegrationTest` (10 parallel threads competing for 1 slot; 5 parallel duplicate queue requests), `IdempotencyIntegrationTest` (replay caching, payload mismatch rejection), and `PostgreSqlConcurrencyIntegrationTest` (Testcontainers).
- **Feature Branch**: `feat/phase2-data-integrity-concurrency`
- **Merge Commit**: `098584f` (merged into `main`)
- **Pull Request**: [#4](https://github.com/rohith-chitturi/GamePulse/pull/4) — **MERGED**
- **Test Results**: 38 automated tests executed (36 passed, 2 Testcontainers tests skipped locally on Windows, executed in CI).
- **CI Quality Gate**: Passed in 1m 28s on Ubuntu runner.
- **Key Decisions**: Pessimistic row locking chosen over optimistic retry loops for high-contention lobby capacity boundaries.

---

## Milestone 5: Recruiter-Ready README Rewrite
- **Date**: 2026-10-29
- **Phase**: Documentation
- **Summary**: Completely rewrote the root `README.md` into an engineering-focused, recruiter-scannable showcase landing page. Added verified technology badges, problem overview, "Engineering Challenges Solved" failure-mode matrix, verified architecture Mermaid diagram, technology stack table, zero-dependency getting started instructions, cURL API examples with valid JSON payloads, concurrency architecture deep dive, test suite breakdown, troubleshooting matrix, and trade-off analysis.
- **Feature Branch**: `docs/enhanced-readme`
- **Merge Commit**: `cf02e14` (merged into `main`)
- **Pull Request**: [#5](https://github.com/rohith-chitturi/GamePulse/pull/5) — **MERGED**
- **Test Results**: 6 core boot and Actuator tests verified via `mvn test -Dtest=GamePulseApplicationTests`.
- **CI Quality Gate**: Passed in 1m 07s on Ubuntu runner.
- **Key Decisions**: Categorized technologies transparently (Implemented vs. Configured vs. Planned); avoided speculative claims.

