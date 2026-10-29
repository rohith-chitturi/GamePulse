# GamePulse Architecture Specification

## 1. System Overview & Context

GamePulse is a production-minded gameplay services platform simulating the backend infrastructure supporting real-time online multiplayer games (such as competitive multiplayer shooters, sports simulations, or battle arenas).

The platform models the lifecycle of multiplayer gameplay:
1. **Player Service**: Registration, profile management, and authentication validation.
2. **Matchmaking Service**: Fair queuing, deterministic matching rules, concurrency protection, and matchmaking ticket lifecycle.
3. **Lobby & Match Session Service**: Authoritative session management, slot reservation, state transitions (`INITIALIZING`, `WAITING_FOR_PLAYERS`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`), and match completion.
4. **Reliability Engine**: Circuit breakers, rate limiters, idempotent operations, and controlled fault injection.
5. **Observability Stack**: Telemetry with Micrometer, Prometheus metrics, structured logging with correlation IDs, and distributed tracing.

```mermaid
flowchart TD
    Client[Game Client / Matchmaker Traffic] --> Gateway[API Gateway / Filter Chain]
    Gateway --> CorrFilter[CorrelationId Filter & Auth Checks]
    CorrFilter --> ReliabilityLayer[Resilience Layer: Rate Limiter & Circuit Breaker]
    
    subgraph Core Gameplay Services [Modular Monolith]
        ReliabilityLayer --> PlayerSvc[Player Service]
        ReliabilityLayer --> MatchSvc[Matchmaking Service]
        ReliabilityLayer --> LobbySvc[Lobby & Session Service]
        
        PlayerSvc --> DB[(PostgreSQL Authoritative DB)]
        LobbySvc --> DB
        MatchSvc --> Redis[(Redis Queue / Distributed State)]
    end

    subgraph Observability & Reliability Verification
        Core Gameplay Services --> Micrometer[Micrometer Metrics]
        Micrometer --> Prometheus[Prometheus Scraper]
        Prometheus --> Grafana[Grafana Dashboards]
        Core Gameplay Services --> StructuredLogs[Structured MDC Logs]
    end
```

---

## 2. Modular Monolith Architecture & Boundaries

To ensure high performance, clear transaction semantics, and straightforward debugging, GamePulse begins as a well-partitioned **modular monolith**. Module boundaries are strictly enforced:

- `com.gamepulse.player`: Player profiles, MMR rating, platform identifiers.
- `com.gamepulse.matchmaking`: Matchmaking queues, tickets, pool management, ticket status queries.
- `com.gamepulse.session`: Lobbies, match sessions, roster management, state transitions.
- `com.gamepulse.reliability`: Fault injection harness, circuit breaker policies, fallback mechanisms.
- `com.gamepulse.telemetry`: Custom metrics collectors, health probes, trace propagators.
- `com.gamepulse.core`: Common infrastructure (filters, exception handling, DTOs, correlation IDs).

---

## 3. Reliability & Fault-Tolerance Principles

1. **Explicit Invariants & DB Constraints**: No duplicate player in queue; no double-joining lobbies; atomic slot reservation using database constraints and pessimistic/optimistic locking where appropriate.
2. **Idempotency**: All mutating operations (e.g., ticket creation, join lobby, complete match) support idempotency keys to prevent duplicate actions during network retries.
3. **Bounded Waits & Timeouts**: All downstream calls, database queries, and cache lookups are bounded by strict timeouts to avoid thread starvation.
4. **Controlled Chaos & Fault Injection**: Configurable test-only fault injectors capable of introducing latency, socket errors, and intermittent exceptions to verify circuit breakers and graceful degradation under traffic.

---

## 4. Telemetry & Metric Cardinality Standards

- Metric tags are strictly bounded: `status`, `method`, `endpoint`, `error_type`.
- Unbounded identifiers (such as `player_id`, `ticket_id`, `match_id`) are **strictly prohibited** from metric tags to prevent metric cardinality explosion.
- Every HTTP request carries `X-Correlation-Id`, stored in SLF4J MDC and emitted in all log statements.
