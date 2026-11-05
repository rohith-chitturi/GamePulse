# GamePulse Performance Benchmark — Profile: HIGH

- **Execution Date**: 2026-11-05 19:46:04 +05:30
- **Target URL**: http://localhost:8080
- **Hardware**: Intel(R) Core(TM) Ultra 5 125H (14 cores), 15.43 GB RAM
- **OS**: Microsoft Windows 11 Home Single Language
- **Duration**: 181.03 seconds

## Performance Summary

| Metric | Measured Value | SLA Target | Compliance |
| :--- | :--- | :--- | :--- |
| **Total Requests** | 47336 | N/A | Completed |
| **Throughput (RPS)** | 262.60 req/s | High | — |
| **Average Latency** | 281.61 ms | < 250 ms | WARN |
| **p50 Latency (Median)** | 191.81 ms | < 100 ms | WARN |
| **p90 Latency** | 660.71 ms | < 350 ms | WARN |
| **p95 Latency** | 871.72 ms | < 500 ms | FAIL |
| **p99 Latency** | 0 ms | < 1200 ms | PASS |
| **Profile Read p95** | 465 ms | < 250 ms | FAIL |
| **Unexpected HTTP Errors** | 0% | < 1.0% | PASS |
| **Active DB Connections** | 0.0 / 10 | Bounded | PASS |
| **JVM Memory Used** | 358.66 MB | Stable | PASS |

## Concurrency & Integrity Verification

- **Redis Cache Hits**: 3886
- **Redis Cache Misses**: 3886
- **Queue Duplicate Rejections (409 DUPLICATE_RESOURCE)**: 1491
- **Lobby Over-Capacity Rejections (409 CAPACITY_EXCEEDED)**: 2154
- **Idempotency Replays**: 2154
