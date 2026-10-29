# GamePulse Engineering Roadmap & Project Plan

## Project Vision & Context
**Target Program**: Electronic Arts Software Engineering / DevOps Internship  
**Project**: GamePulse — Chaos-Resilient Gameplay Services Platform  
**Architecture**: Modular Monolith → Event-Driven Scalable Backend  
**Core Question**: *Can we demonstrate, with repeatable experiments, how backend reliability engineering protects a multiplayer gaming service when dependencies fail or traffic increases?*

---

## Phase Delivery Tracking Table

| Phase | Title | Branch | PR # | PR Status | Review Status | CI Quality Gate |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Phase 0** | Repository & Project Foundation | `feat/phase0-project-foundation` | [#1](https://github.com/rohith-chitturi/GamePulse/pull/1) | OPEN | Passed (Self-Review / CI Verified) | Passing |
| **Phase 1** | Gameplay Backend Services | `feat/phase1-gameplay-backend` | - | Planned | - | - |
| **Phase 2** | Data Integrity & Concurrency | `feat/phase2-data-integrity-concurrency` | - | Planned | - | - |
| **Phase 3** | Redis & Service Resilience | `feat/phase3-redis-resilience` | - | Planned | - | - |
| **Phase 4** | Observability & Telemetry | `feat/phase4-observability-telemetry` | - | Planned | - | - |
| **Phase 5** | Load Testing & Reliability Experiments | `feat/phase5-load-testing-experiments` | - | Planned | - | - |
| **Phase 6** | Docker & CI/CD Hardening | `feat/phase6-docker-cicd-hardening` | - | Planned | - | - |
| **Phase 7** | Operations Dashboard | `feat/phase7-operations-dashboard` | - | Planned | - | - |
| **Phase 8** | Advanced Differentiator | `feat/phase8-differentiator` | - | Planned | - | - |
| **Phase 9** | Final Engineering Review & Case Study | `feat/phase9-final-review` | - | Planned | - | - |

---

## Detailed Phase Breakdown & Acceptance Criteria

### Phase 0 — Repository and Project Foundation
- **Goals**:
  - Environment inspection & fact verification.
  - Maven multi-dependency architecture (Spring Boot 3.3.5, Java 17 LTS, Actuator, JPA, Micrometer).
  - Reproducible build tooling (`mvnw`, `mvnw.cmd`).
  - Correlation ID filter & baseline system metadata endpoint.
  - Automated CI workflow on GitHub Actions (`.github/workflows/ci.yml`).
  - CodeRabbit review configuration (`.coderabbit.yaml`).
  - Project architecture & planning documentation.
- **Acceptance Criteria**:
  - Clean `mvn test` execution with 100% test pass rate.
  - PR opened on GitHub targeting `main` branch with verified CI status.

### Phase 1 — Gameplay Backend Services
- **Goals**:
  - Player Service: registration, profiles, MMR, validation, error responses.
  - Matchmaking Service: deterministic FIFO queue with MMR windowing, ticket lifecycle (`QUEUED`, `MATCHED`, `CANCELLED`, `EXPIRED`).
  - Lobby and Match Session Service: lobby creation, joining, capacity validation, state machine (`INITIALIZING`, `WAITING_FOR_PLAYERS`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`).
  - Authoritative persistence in PostgreSQL / H2 test profile.
  - OpenAPI/Swagger documentation.
- **Acceptance Criteria**:
  - Complete gameplay lifecycle tested end-to-end via REST APIs.

### Phase 2 — Data Integrity and Concurrency
- **Goals**:
  - Idempotency layer with unique keys for mutating actions.
  - Concurrency control: prevent double-joining, over-capacity lobbies, or duplicate queue entries under race conditions.
  - Transaction boundaries and explicit database constraints (`UNIQUE`, foreign keys).
  - Multi-threaded concurrency integration tests.
- **Acceptance Criteria**:
  - Zero duplicate matches or roster corruptions under parallel load.

### Phase 3 — Redis and Service Resilience
- **Goals**:
  - Redis-backed distributed state & queue coordination.
  - Resilience4j integration: circuit breakers, bounded request timeouts, rate limiters.
  - Safe retry policies with exponential backoff and full jitter.
  - Controlled fault injection harness (latency, dependency failure injection).
- **Acceptance Criteria**:
  - Circuit breaker transitions (`CLOSED` → `OPEN` → `HALF_OPEN`) verified under injected failure.

### Phase 4 — Observability
- **Goals**:
  - Micrometer custom metrics: matchmaking queue depth, wait duration, match creation success/fail rates.
  - Prometheus scraping & Grafana dashboard configurations.
  - Structured logging with MDC correlation IDs and trace propagation.
  - Failure investigation runbook.
- **Acceptance Criteria**:
  - Telemetry captures live state and failures with zero unbounded tag cardinality.

### Phase 5 — Load Testing and Reliability Experiments
- **Goals**:
  - k6 load test scripts (normal, burst, slow dependency, recovery scenarios).
  - Repeatable empirical experiments with hypothesis, baseline, injection, fix, and post-fix metrics.
  - Documented before/after latency (p50/p95/p99) and error rates.
- **Acceptance Criteria**:
  - Defensible, unmanipulated benchmark reports.

### Phase 6 — Docker and CI/CD Hardening
- **Goals**:
  - Multi-stage Dockerfile and Docker Compose environment (GamePulse app, PostgreSQL, Redis, Prometheus, Grafana).
  - GitHub Actions matrix testing, container image build, security scanning.
- **Acceptance Criteria**:
  - Single-command local environment launch (`docker compose up`).

### Phase 7 — Operations Dashboard
- **Goals**:
  - React + TypeScript operational dashboard displaying live matchmaker queues, service health, and active circuit breakers.
- **Acceptance Criteria**:
  - Real-time dashboard connected to actual backend APIs and metrics.

### Phase 8 — Advanced Differentiator
- **Goals**:
  - Dependency failure propagation visualizer or reliability regression quality gate.
- **Acceptance Criteria**:
  - Functional differentiator tested and integrated.

### Phase 9 — Final Engineering Review
- **Goals**:
  - End-to-end audit, security check, clean documentation, reproduction guide, and engineering case study.
- **Acceptance Criteria**:
  - Production-ready portfolio artifact for EA Gameplay Services review.
