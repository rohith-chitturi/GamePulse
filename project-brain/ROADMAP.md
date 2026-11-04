# GamePulse — Engineering Roadmap & Phased Execution Plan

> **Master Phased Execution Tracking**  
> **Status Conventions**: `COMPLETED`, `IN_PROGRESS`, `PLANNED`, `BLOCKED`, `DEFERRED`

---

## 1. Milestone Tracking Matrix

| Phase | Milestone Title | Target Branch | PR # | PR Status | CI Quality Gate | Verified Evidence |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Phase 0** | Repository & Project Foundation | `feat/phase0-project-foundation` | [#1](https://github.com/rohith-chitturi/GamePulse/pull/1) | **MERGED** | Passed (24s) | Spring Boot 3.3.5, Java 17 LTS, CorrelationIdFilter, OpenAPI |
| **Phase 1** | Gameplay Backend Services | `feat/phase1-gameplay-backend` | [#2](https://github.com/rohith-chitturi/GamePulse/pull/2) | **MERGED** | Passed (19s) | Player profiles, deterministic matchmaking, lobby lifecycles |
| **Namespace Refactor** | Root Namespace Standardization | `refactor/standardize-package-namespace` | [#3](https://github.com/rohith-chitturi/GamePulse/pull/3) | **MERGED** | Passed (40s) | Migrated all 43 classes to `com.gamepulse` |
| **Phase 2** | Data Integrity & Concurrency | `feat/phase2-data-integrity-concurrency` | [#4](https://github.com/rohith-chitturi/GamePulse/pull/4) | **MERGED** | Passed (1m 28s) | Idempotency engine, pessimistic locking, 38 tests verified |
| **Docs Refactor** | Recruiter-Ready README Rewrite | `docs/enhanced-readme` | [#5](https://github.com/rohith-chitturi/GamePulse/pull/5) | **MERGED** | Passed (1m 07s) | Complete rewrite with architecture diagrams and API docs |
| **Phase 6** | Docker & Container Infrastructure | `feat/docker-infrastructure` | [#6](https://github.com/rohith-chitturi/GamePulse/pull/6) | **IN PROGRESS** | Pending PR CI | Multi-stage Dockerfile, 5-service compose stack (App, DB, Redis, Prom, Grafana), health checks, persistent storage verified |
| **Phase 3** | Redis & Service Resilience | `feat/phase3-redis-resilience` | Pending | **PLANNED** (Next) | - | Resilience4j circuit breakers, rate limiters, fault injection |
| **Phase 4** | Observability & Telemetry | `feat/phase4-observability-telemetry` | Pending | **PLANNED** | - | Custom Micrometer metrics (queue depth, match latency), Grafana |
| **Phase 5** | Load Testing & Reliability Experiments | `feat/phase5-load-testing-experiments` | Pending | **PLANNED** | - | Repeatable k6 traffic, chaos fault injection, empirical report |
| **Phase 7** | Operations Dashboard & Portfolio Polish | `feat/phase7-operations-dashboard` | Pending | **PLANNED** | - | Operations dashboard, runbooks, final presentation |

---

## 2. Detailed Phase Specifications

### Completed Milestones

#### Phase 0 — Foundation & CI Quality Gate
- **Acceptance Criteria**: Spring Boot 3.3.5 baseline, Java 17 LTS, Maven Wrapper, Actuator health/Prometheus scraping, Correlation ID filter, GitHub Actions CI workflow.
- **Evidence**: PR #1 merged. 5 baseline tests passed in 24s.

#### Phase 1 — Gameplay Backend Services
- **Acceptance Criteria**: Player Service (MMR, validations), Matchmaking Service (deterministic FIFO queue with windowed MMR search), Lobby & Match Session Service (state machines, roster checks), OpenAPI Swagger UI.
- **Evidence**: PR #2 merged. 30 tests passed in 19s.

#### Namespace Standardization
- **Acceptance Criteria**: 100% elimination of vendor-specific naming; root package migrated to `com.gamepulse` across all 43 classes, directories, POM, and configs.
- **Evidence**: PR #3 merged. 31 tests passed in 40s.

#### Phase 2 — Data Integrity & Concurrency Controls
- **Acceptance Criteria**: Application-level idempotency filter (`Idempotency-Key`, SHA-256 body hash, 24h TTL, 120s crash lease reclaim, response replay), pessimistic row locking on lobby slots (`PESSIMISTIC_WRITE`), optimistic entity versioning (`@Version`), multi-threaded concurrency test suites.
- **Evidence**: PR #4 merged. 38 tests passed in 1m 28s. Parallel lobby join race condition tests verified zero over-capacity breaches.

#### Recruiter-Ready README Rewrite
- **Acceptance Criteria**: Professional README rewritten from scratch covering architecture diagrams, engineering challenges solved, cURL examples, test evidence, and limitations.
- **Evidence**: PR #5 merged. CI passed in 1m 07s.

---

### Upcoming Milestones (Prioritized by Portfolio Impact)

#### Milestone 1: Phase 6 — Docker & Container Infrastructure (Highest Immediate Value)
- **Goal**: Enable single-command local reproducibility (`docker compose up`) and containerized deployment.
- **Dependencies**: Completed Phases 0–2.
- **Acceptance Criteria**:
  1. Multi-stage production `Dockerfile` with eclipse-temurin:17-jre runtime and non-root security user.
  2. `docker-compose.yml` orchestrating GamePulse app, PostgreSQL 15, Redis 7 Alpine, and Prometheus scraper.
  3. Container health checks and environment variable pass-through.
  4. Build and test verification in CI pipeline.
- **Branch**: `feat/docker-infrastructure`

#### Milestone 2: Phase 3 — Redis & Service Resilience
- **Goal**: Protect gameplay services against cascading dependency failures and traffic bursts.
- **Dependencies**: Docker infrastructure running Redis.
- **Acceptance Criteria**:
  1. Resilience4j circuit breakers on external service interfaces.
  2. Bounded request timeouts and rate limiters.
  3. Controlled test fault-injection controller (`/api/v1/test/chaos/latency`, `/api/v1/test/chaos/exception`).
  4. Verified circuit breaker state transitions (`CLOSED` $\rightarrow$ `OPEN` $\rightarrow$ `HALF_OPEN`).
- **Branch**: `feat/phase3-redis-resilience`

#### Milestone 3: Phase 4 — Observability & Telemetry
- **Goal**: Provide real-time operational visibility into gameplay services.
- **Dependencies**: Micrometer baseline from Phase 0.
- **Acceptance Criteria**:
  1. Custom Micrometer meters: `gameplay.matchmaking.queue.depth` (Gauge), `gameplay.matchmaking.wait.time` (Timer), `gameplay.matches.created` (Counter).
  2. Prometheus scrape configuration and Grafana dashboard provisioning JSON.
  3. Structured MDC logging with correlation IDs.
- **Branch**: `feat/phase4-observability-telemetry`

#### Milestone 4: Phase 5 — Load Testing & Reliability Experiments
- **Goal**: Empirically answer the core project question with before-and-after measurements.
- **Dependencies**: Phase 3 (Resilience) & Phase 4 (Observability).
- **Acceptance Criteria**:
  1. Automated k6 load testing scripts (normal traffic vs. burst spikes).
  2. Repeatable chaos experiment: baseline measurement $\rightarrow$ inject latency/fault $\rightarrow$ observe degradation $\rightarrow$ enable circuit breaker/retry $\rightarrow$ verify recovery.
  3. Recorded empirical results table with p50, p95, p99 latencies and error rates.
- **Branch**: `feat/phase5-load-testing-experiments`

