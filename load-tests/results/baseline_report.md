# GamePulse Performance Benchmark — Profile: BASELINE

- **Execution Date**: 2026-11-05 19:06:41 +05:30
- **Target URL**: http://localhost:8080
- **Hardware**: Intel(R) Core(TM) Ultra 5 125H (14 cores), 15.43 GB RAM
- **OS**: Microsoft Windows 11 Home Single Language
- **Duration**: 121.12 seconds

## Performance Summary

| Metric | Measured Value | SLA Target | Compliance |
| :--- | :--- | :--- | :--- |
| **Total Requests** | 25002 | N/A | Completed |
| **Throughput (RPS)** | 207.78 req/s | High | — |
| **Average Latency** | 62.80 ms | < 250 ms | PASS |
| **p50 Latency (Median)** | 30.56 ms | < 100 ms | PASS |
| **p90 Latency** | 158.17 ms | < 350 ms | PASS |
| **p95 Latency** | 205.19 ms | < 500 ms | PASS |
| **p99 Latency** | 0 ms | < 1200 ms | PASS |
| **Profile Read p95** | 45.55 ms | < 250 ms | PASS |
| **Unexpected HTTP Errors** | 0% | < 1.0% | PASS |
| **Active DB Connections** | 0.0 / 10 | Bounded | PASS |
| **JVM Memory Used** | 250.12 MB | Stable | PASS |

## Concurrency & Integrity Verification

- **Redis Cache Hits**: 2035
- **Redis Cache Misses**: 2035
- **Queue Duplicate Rejections (409 DUPLICATE_RESOURCE)**: 734
- **Lobby Over-Capacity Rejections (409 CAPACITY_EXCEEDED)**: 1161
- **Idempotency Replays**: 1161
