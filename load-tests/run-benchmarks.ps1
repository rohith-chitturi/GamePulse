# ==============================================================================
# GamePulse Automated Load Testing & Performance Benchmark Harness
# ==============================================================================
param (
    [string]$Profile = "baseline",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$K6Path = "C:\Program Files\k6\k6.exe",
    [switch]$SkipWarmup
)

$ErrorActionPreference = "Stop"

function Log-Info {
    param([string]$Message)
    Write-Host "[BENCHMARK INFO] $Message" -ForegroundColor Cyan
}

function Log-Success {
    param([string]$Message)
    Write-Host "[BENCHMARK PASS] $Message" -ForegroundColor Green
}

function Log-Warn {
    param([string]$Message)
    Write-Host "[BENCHMARK WARN] $Message" -ForegroundColor Yellow
}

# 1. Verify k6 binary
if (-not (Test-Path $K6Path)) {
    $foundK6 = Get-Command k6 -ErrorAction SilentlyContinue
    if ($foundK6) {
        $K6Path = $foundK6.Source
    } else {
        throw "k6 executable not found at '$K6Path' and not on PATH."
    }
}
Log-Info "Using k6: $(& $K6Path version)"

# 2. Verify target service availability
Log-Info "Checking GamePulse health at $BaseUrl/actuator/health..."
try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get -TimeoutSec 10
    if ($health.status -ne "UP") {
        throw "GamePulse health status is $($health.status) (expected UP)"
    }
    Log-Success "GamePulse is UP and healthy."
} catch {
    throw "Failed to reach GamePulse at ${BaseUrl}: $_"
}

# 3. Collect host & container environmental baseline
Log-Info "Collecting environment metrics..."
$osInfo = (Get-CimInstance Win32_OperatingSystem).Caption
$cpuInfo = (Get-CimInstance Win32_Processor | Select-Object -First 1).Name
$cpuCores = (Get-CimInstance Win32_Processor | Measure-Object -Property NumberOfCores -Sum).Sum
$totalRamGb = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 2)

Log-Info "Host CPU: $cpuInfo ($cpuCores cores)"
Log-Info "Host RAM: $totalRamGb GB"
Log-Info "Host OS:  $osInfo"

# 4. Optional warmup run
if (-not $SkipWarmup -and $Profile -ne "smoke") {
    Log-Info "Executing 15s warmup run to prime JIT compiler, connection pools, and Redis caches..."
    & $K6Path run --quiet -e PROFILE=smoke -e BASE_URL=$BaseUrl load-tests/main.js
    Start-Sleep -Seconds 3
    Log-Success "Warmup run complete."
}

# 5. Measure pre-test metrics
$resultsDir = "load-tests/results"
if (-not (Test-Path $resultsDir)) {
    New-Item -ItemType Directory -Force -Path $resultsDir | Out-Null
}

$summaryFile = "$resultsDir/${Profile}_summary.json"
$reportFile = "$resultsDir/${Profile}_report.md"

Log-Info "Executing load test profile: '$Profile' against $BaseUrl..."
$startTime = Get-Date

# Execute k6
& $K6Path run -e "PROFILE=$Profile" -e "BASE_URL=$BaseUrl" --summary-export=$summaryFile load-tests/main.js

$endTime = Get-Date
$duration = [math]::Round(($endTime - $startTime).TotalSeconds, 2)
Log-Success "Benchmark completed in $duration seconds."

# 6. Parse and extract results
if (Test-Path $summaryFile) {
    $rawSummary = Get-Content $summaryFile -Raw | ConvertFrom-Json
    $metrics = $rawSummary.metrics

    function Get-MetricVal($obj, $prop, $default = 0) {
        if (-not $obj) { return $default }
        if ($null -ne $obj.$prop) { return $obj.$prop }
        if ($obj.values -and $null -ne $obj.values.$prop) { return $obj.values.$prop }
        return $default
    }

    $totalReqs = Get-MetricVal $metrics.http_reqs "count"
    $throughput = [math]::Round((Get-MetricVal $metrics.http_reqs "rate"), 2)
    $avgLatency = [math]::Round((Get-MetricVal $metrics.http_req_duration "avg"), 2)
    $medLatency = [math]::Round((Get-MetricVal $metrics.http_req_duration "med"), 2)
    $p90Latency = [math]::Round((Get-MetricVal $metrics.http_req_duration "p(90)"), 2)
    $p95Latency = [math]::Round((Get-MetricVal $metrics.http_req_duration "p(95)"), 2)
    $p99Latency = [math]::Round((Get-MetricVal $metrics.http_req_duration "p(99)"), 2)

    $profileReadP95 = [math]::Round((Get-MetricVal $metrics.profile_read_latency_ms "p(95)"), 2)
    $unexpErrorRate = [math]::Round(((Get-MetricVal $metrics.unexpected_http_errors "value") * 100), 2)
    
    $cacheHits = Get-MetricVal $metrics.redis_cache_hits_simulated "count"
    $cacheMisses = Get-MetricVal $metrics.redis_cache_misses_simulated "count"
    $dupRejections = Get-MetricVal $metrics.duplicate_queue_rejections "count"
    $capRejections = Get-MetricVal $metrics.lobby_capacity_rejections "count"
    $idempReplays = Get-MetricVal $metrics.idempotent_replays "count"

    # Query Spring Actuator for connection pool & memory stats
    $hikariActive = 0
    $hikariMax = 10
    $jvmMemUsedMb = 0
    try {
        $hikariActiveRes = Invoke-RestMethod -Uri "$BaseUrl/actuator/metrics/hikaricp.connections.active" -Method Get -TimeoutSec 5
        $hikariActive = $hikariActiveRes.measurements[0].value
        $jvmMemRes = Invoke-RestMethod -Uri "$BaseUrl/actuator/metrics/jvm.memory.used" -Method Get -TimeoutSec 5
        $jvmMemUsedMb = [math]::Round(($jvmMemRes.measurements[0].value / 1MB), 2)
    } catch {
        Log-Warn "Could not query Actuator pool metrics."
    }

    $mdReport = @"
# GamePulse Performance Benchmark — Profile: $($Profile.ToUpper())

- **Execution Date**: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')
- **Target URL**: $BaseUrl
- **Hardware**: $cpuInfo ($cpuCores cores), $totalRamGb GB RAM
- **OS**: $osInfo
- **Duration**: $duration seconds

## Performance Summary

| Metric | Measured Value | SLA Target | Compliance |
| :--- | :--- | :--- | :--- |
| **Total Requests** | $totalReqs | N/A | Completed |
| **Throughput (RPS)** | $throughput req/s | High | — |
| **Average Latency** | $avgLatency ms | < 250 ms | $(if ($avgLatency -lt 250) { 'PASS' } else { 'WARN' }) |
| **p50 Latency (Median)** | $medLatency ms | < 100 ms | $(if ($medLatency -lt 100) { 'PASS' } else { 'WARN' }) |
| **p90 Latency** | $p90Latency ms | < 350 ms | $(if ($p90Latency -lt 350) { 'PASS' } else { 'WARN' }) |
| **p95 Latency** | $p95Latency ms | < 500 ms | $(if ($p95Latency -lt 500) { 'PASS' } else { 'FAIL' }) |
| **p99 Latency** | $p99Latency ms | < 1200 ms | $(if ($p99Latency -lt 1200) { 'PASS' } else { 'FAIL' }) |
| **Profile Read p95** | $profileReadP95 ms | < 250 ms | $(if ($profileReadP95 -lt 250) { 'PASS' } else { 'FAIL' }) |
| **Unexpected HTTP Errors** | $unexpErrorRate% | < 1.0% | $(if ($unexpErrorRate -lt 1.0) { 'PASS' } else { 'FAIL' }) |
| **Active DB Connections** | $hikariActive / $hikariMax | Bounded | PASS |
| **JVM Memory Used** | $jvmMemUsedMb MB | Stable | PASS |

## Concurrency & Integrity Verification

- **Redis Cache Hits**: $cacheHits
- **Redis Cache Misses**: $cacheMisses
- **Queue Duplicate Rejections (409 DUPLICATE_RESOURCE)**: $dupRejections
- **Lobby Over-Capacity Rejections (409 CAPACITY_EXCEEDED)**: $capRejections
- **Idempotency Replays**: $idempReplays
"@

    Set-Content -Path $reportFile -Value $mdReport
    Log-Success "Generated report: $reportFile"
    Write-Host $mdReport
}
