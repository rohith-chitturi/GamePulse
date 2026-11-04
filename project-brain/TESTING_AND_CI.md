# GamePulse — Testing & CI/CD Specification

> **Verifiable Quality Record**  
> **Rule**: A test that is skipped locally is not a local pass. A CI pass applies only to the checks that actually executed. Every claim must record command, environment, and timestamp.

---

## 1. Test Suite Catalog & Breakdown

The test suite consists of **38 automated tests** spanning unit, web layer, integration, concurrency, and containerized database validation:

| Test Class | Category | Test Count | Key Invariants Verified |
| :--- | :--- | :--- | :--- |
| **`GamePulseApplicationTests`** | Boot & Actuator | 6 | Spring context boot, `/api/v1/system/info`, `/actuator/health`, correlation ID propagation, `/actuator/prometheus`, `/v3/api-docs` schema generation. |
| **`PlayerServiceTest`** | Unit | 5 | Player creation, initial MMR defaults, duplicate username checks, status transitions. |
| **`PlayerIntegrationTest`** | REST Integration | 4 | Bean validation failures (RFC 7807 400 Bad Request), duplicate player registration (409 Conflict), pagination queries. |
| **`MatchmakingServiceTest`** | Unit | 6 | FIFO queue ordering, dynamic MMR window expansion, queue cancellation, duplicate queue rejection. |
| **`MatchSessionServiceTest`** | Unit | 7 | Lobby creation, player joining, team auto-balancing, start transition, completion winner recording, invalid state transitions. |
| **`GameplayIntegrationTest`** | E2E Integration | 3 | Full multiplayer gameplay loop: registration $\rightarrow$ matchmaking $\rightarrow$ lobby session $\rightarrow$ start match $\rightarrow$ match completion. |
| **`IdempotencyIntegrationTest`** | REST Integration | 3 | Identical request replay with cached response (`Idempotency-Replayed: true`), conflicting key payload rejection (409 Conflict), lobby creation idempotency. |
| **`ConcurrencyIntegrationTest`** | Concurrency | 2 | Parallel 10-thread lobby join burst at 1-slot boundary; 5-thread parallel duplicate queue entry burst. |
| **`PostgreSqlConcurrencyIntegrationTest`** | Container Integration | 2 | Real PostgreSQL container verification using Testcontainers (`postgres:15-alpine`). |

---

## 2. Test Execution Commands

### Run Full Test Suite (Local Machine)
```bash
# Linux / macOS:
./mvnw clean test

# Windows PowerShell:
.\mvnw.cmd clean test
```

### Run Concurrency & Idempotency Tests Only
```bash
./mvnw test -Dtest=ConcurrencyIntegrationTest,IdempotencyIntegrationTest
```

### Run Smoke Tests & OpenAPI Documentation Verification
```bash
./mvnw test -Dtest=GamePulseApplicationTests
```

---

## 3. Concurrency Test Mechanics

### Parallel Lobby Joins (`testParallelLobbyJoinsAtCapacityBoundary`)
- **Setup**: Creates a 2-player lobby (`maxPlayers = 2`) with 1 existing player (1 slot remaining).
- **Hazard**: 10 separate worker threads attempt to join the lobby simultaneously with distinct player IDs.
- **Synchronization**: Uses `CountDownLatch(1)` as a starting gun. All 10 threads block until the latch countdown fires, releasing them at the exact same millisecond.
- **Assertion**:
  - Exactly 1 thread completes with HTTP status `200 OK`.
  - Exactly 9 threads receive HTTP status `409 Conflict` with error code `CAPACITY_EXCEEDED`.
  - Direct database inspection asserts `session.getPlayers().size() == 2`.

### Simultaneous Queue Requests (`testConcurrentDuplicateEnqueueRejected`)
- **Setup**: A single player profile is created.
- **Hazard**: 5 separate threads attempt to call `/api/v1/matchmaking/queue` simultaneously for the same `playerId`.
- **Assertion**:
  - Exactly 1 thread receives HTTP status `201 Created`.
  - Exactly 4 threads receive HTTP status `409 Conflict` with error code `DUPLICATE_RESOURCE`.
  - Direct database query asserts `ticketRepository.findByPlayerIdAndStatus(playerId, QUEUED).size() == 1`.

---

## 4. Testcontainers Configuration & Local vs. CI Behavior

- **Image**: `postgres:15-alpine`
- **Annotation**: `@Testcontainers(disabledWithoutDocker = true)`
- **Host Behavior (Windows)**:
  - On Windows developer environments where Docker Desktop operates via WSL2 named pipe translation (`npipe://`), Testcontainers may encounter `BadRequestException (Status 400)` when querying the Docker daemon.
  - The `disabledWithoutDocker = true` flag gracefully skips the 2 container tests locally, preventing workstation build blockage while logging an informative warning.
- **CI Runner Behavior (Ubuntu)**:
  - In GitHub Actions (`ubuntu-latest`), Docker runs natively via standard Unix domain socket (`/var/run/docker.sock`).
  - Testcontainers detects the daemon, pulls `postgres:15-alpine`, and executes all tests against real PostgreSQL.

---

## 5. GitHub Actions Continuous Integration

### Workflow Specification (`.github/workflows/ci.yml`)
- **Triggers**: Pushes and Pull Requests targeting `main`.
- **Environment**: `ubuntu-latest`.
- **Java Setup**: `actions/setup-java@v4` with `temurin` distribution, version `17`, Maven caching enabled.
- **Verification Steps**:
  1. `./mvnw clean verify -B` (Compile, unit test, integration test, Testcontainers PostgreSQL).
  2. `docker compose config` (Validate Docker Compose schema and variable substitution).
  3. `docker build -t gamepulse:ci .` (Validate multi-stage Docker build and packaging).
- **Artifacts**: Uploads Surefire reports on failure (`target/surefire-reports/`).

### Historical CI Runs Record

| Run ID | Branch / PR | Commit | Trigger | Duration | Result |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **[40836057597](https://github.com/rohith-chitturi/GamePulse/actions/runs/40836057597)** | PR #2 (`feat/phase1-gameplay-backend`) | `57a7fb0` | push | 45s | **SUCCESS** |
| **[40859200822](https://github.com/rohith-chitturi/GamePulse/actions/runs/40859200822)** | PR #3 (`refactor/standardize-package-namespace`) | `65d4cca` | pull_request | 44s | **SUCCESS** |
| **[40859400533](https://github.com/rohith-chitturi/GamePulse/actions/runs/40859400533)** | `main` (Merge PR #3) | `7d987a7` | push | 41s | **SUCCESS** |
| **[40908914312](https://github.com/rohith-chitturi/GamePulse/actions/runs/40908914312)** | PR #4 (`feat/phase2-data-integrity-concurrency`) | `098584f` | pull_request | 1m 28s | **SUCCESS** |
| **[40911503554](https://github.com/rohith-chitturi/GamePulse/actions/runs/40911503554)** | PR #5 (`docs/enhanced-readme`) | `cf02e14` | pull_request | 1m 07s | **SUCCESS** |

