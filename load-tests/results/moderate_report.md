# GamePulse Performance Benchmark — Profile: MODERATE

- **Execution Date**: 2026-11-05 19:16:19 +05:30
- **Target URL**: http://localhost:8080
- **Hardware**: Intel(R) Core(TM) Ultra 5 125H (14 cores), 15.43 GB RAM
- **OS**: Microsoft Windows 11 Home Single Language
- **Duration**: 181.7 seconds

## Performance Summary

| Metric | Measured Value | SLA Target | Compliance |
| :--- | :--- | :--- | :--- |
| **Total Requests** | 46370 | N/A | Completed |
| **Throughput (RPS)** | 257.55 req/s | High | — |
| **Average Latency** | 124.68 ms | < 250 ms | PASS |
| **p50 Latency (Median)** | 69.40 ms | < 100 ms | PASS |
| **p90 Latency** | 312.56 ms | < 350 ms | PASS |
| **p95 Latency** | 389.50 ms | < 500 ms | PASS |
| **p99 Latency** | 0 ms | < 1200 ms | PASS |
| **Profile Read p95** | 123 ms | < 250 ms | PASS |
| **Unexpected HTTP Errors** | 0% | < 1.0% | PASS |
| **Active DB Connections** | 0.0 / 10 | Bounded | PASS |
| **JVM Memory Used** | 340.24 MB | Stable | PASS |

## Concurrency & Integrity Verification

- **Redis Cache Hits**: 3766
- **Redis Cache Misses**: 3766
- **Queue Duplicate Rejections (409 DUPLICATE_RESOURCE)**: 1528
- **Lobby Over-Capacity Rejections (409 CAPACITY_EXCEEDED)**: 2117
- **Idempotency Replays**: 2117
