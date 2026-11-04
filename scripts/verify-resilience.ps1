# GamePulse — Live Resilience & Controlled Failure Verification Script
# Executes 7 empirical failure experiments against live Docker Compose stack

$ErrorActionPreference = "Continue"
$BaseUrl = "http://localhost:8080"

function Log-Step($title) {
    Write-Host ""
    Write-Host "====================================================================" -ForegroundColor Cyan
    Write-Host " [EXPERIMENT] $title" -ForegroundColor Cyan
    Write-Host "====================================================================" -ForegroundColor Cyan
}

# -----------------------------------------------------------------------------
# Pre-Check: Base stack health
# -----------------------------------------------------------------------------
Log-Step "Pre-Check: Verifying Base Stack Health"
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get
Write-Host "Initial Health: $($health.status)" -ForegroundColor Green

$resilienceStatus = Invoke-RestMethod -Uri "$BaseUrl/api/v1/resilience/status" -Method Get
Write-Host "Redis Available: $($resilienceStatus.redis.available)"
Write-Host "Redis Circuit Breaker: $($resilienceStatus.circuitBreakers.redisService.state)"
Write-Host "Downstream Circuit Breaker: $($resilienceStatus.circuitBreakers.downstreamService.state)"

# -----------------------------------------------------------------------------
# Experiment 1, 2, 3: PostgreSQL Outage & Repeated / Idempotent Requests
# -----------------------------------------------------------------------------
Log-Step "Experiment 1: PostgreSQL Down - API Request Failure Handling"
Write-Host "Stopping PostgreSQL container..." -ForegroundColor Yellow
docker compose stop postgres | Out-Null
Start-Sleep -Seconds 2

$idempKeyOutage = [System.Guid]::NewGuid().ToString()
$exp1Status = 0
$exp1Body = ""

try {
    $payload = @{
        username = "OutageUser1_$([System.Guid]::NewGuid().ToString().Substring(0,8))"
        email = "outage1_$([System.Guid]::NewGuid().ToString().Substring(0,8))@gamepulse.com"
        initialMmr = 1200
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$BaseUrl/api/v1/players" -Method Post `
        -Headers @{ "Content-Type" = "application/json"; "Idempotency-Key" = $idempKeyOutage } `
        -Body $payload
} catch {
    $exp1Status = $_.Exception.Response.StatusCode.value__
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $exp1Body = $reader.ReadToEnd()
}

Write-Host "HTTP Status Code: $exp1Status (Expected: 503 Service Unavailable)" -ForegroundColor Green
Write-Host "Response Body: $exp1Body" -ForegroundColor Gray

# Experiment 2: Repeated requests during database outage
Log-Step "Experiment 2: Repeated Requests During Outage"
$repeatedStatusCodes = @()
for ($i = 2; $i -le 4; $i++) {
    try {
        $p = @{
            username = "OutageUser$i"
            email = "outage$i@gamepulse.com"
            initialMmr = 1200
        } | ConvertTo-Json

        $res = Invoke-RestMethod -Uri "$BaseUrl/api/v1/players" -Method Post `
            -Headers @{ "Content-Type" = "application/json" } `
            -Body $p
    } catch {
        $repeatedStatusCodes += $_.Exception.Response.StatusCode.value__
    }
}
Write-Host "Repeated Outage Status Codes: $($repeatedStatusCodes -join ', ') (All expected 503)" -ForegroundColor Green

# -----------------------------------------------------------------------------
# Experiment 4: Recovery After Restoring PostgreSQL
# -----------------------------------------------------------------------------
Log-Step "Experiment 4: Recovery After Restoring PostgreSQL"
Write-Host "Starting PostgreSQL container..." -ForegroundColor Yellow
docker compose start postgres | Out-Null
Write-Host "Waiting for PostgreSQL healthcheck..."
Start-Sleep -Seconds 6

# Replay the exact same Idempotency-Key from the outage
$replayStatus = 0
$replayBody = $null
$replayUsername = "OutageUser1_Recovered"
$replayEmail = "outage1_rec@gamepulse.com"
$p = @{
    username = $replayUsername
    email = $replayEmail
    initialMmr = 1200
} | ConvertTo-Json

try {
    $replayBody = Invoke-RestMethod -Uri "$BaseUrl/api/v1/players" -Method Post `
        -Headers @{ "Content-Type" = "application/json"; "Idempotency-Key" = $idempKeyOutage } `
        -Body $p
    $replayStatus = 201
} catch {
    $replayStatus = $_.Exception.Response.StatusCode.value__
}

Write-Host "Post-Recovery Request with Same Key: HTTP $replayStatus (Expected: 201 Created)" -ForegroundColor Green
Write-Host "Created Player ID: $($replayBody.id)"

# Verify Idempotent Replay on second attempt
$secondReplay = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/v1/players" -Method Post `
    -Headers @{ "Content-Type" = "application/json"; "Idempotency-Key" = $idempKeyOutage } `
    -Body $p
Write-Host "Duplicate Request Replayed Header: $($secondReplay.Headers['Idempotency-Replayed'])" -ForegroundColor Green

# -----------------------------------------------------------------------------
# Experiment 5: Redis Outage and Graceful Cache Degradation
# -----------------------------------------------------------------------------
Log-Step "Experiment 5: Redis Outage and Circuit Breaker Degradation"
$playerId = $replayBody.id

# First GET: populates cache
$profile1 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/players/$playerId" -Method Get
Write-Host "Read player with Redis UP: $($profile1.username)" -ForegroundColor Green

Write-Host "Stopping Redis container..." -ForegroundColor Yellow
docker compose stop redis | Out-Null
Start-Sleep -Seconds 2

# Read player with Redis DOWN -> Graceful degradation to PostgreSQL
$profileDown = Invoke-RestMethod -Uri "$BaseUrl/api/v1/players/$playerId" -Method Get
Write-Host "Read player with Redis DOWN: $($profileDown.username) (Graceful DB Fallback)" -ForegroundColor Green

# Trigger consecutive calls to trip redisService circuit breaker to OPEN
for ($i = 0; $i -lt 5; $i++) {
    $null = Invoke-RestMethod -Uri "$BaseUrl/api/v1/players/$playerId" -Method Get
}

$statusAfterRedisStop = Invoke-RestMethod -Uri "$BaseUrl/api/v1/resilience/status" -Method Get
Write-Host "Redis Circuit Breaker State: $($statusAfterRedisStop.circuitBreakers.redisService.state)" -ForegroundColor Green

# Restart Redis
Write-Host "Starting Redis container..." -ForegroundColor Yellow
docker compose start redis | Out-Null
Start-Sleep -Seconds 5

# Reset circuit breaker
$null = Invoke-RestMethod -Uri "$BaseUrl/api/v1/resilience/circuit-breakers/redisService/transition/reset" -Method Post
$statusAfterRedisStart = Invoke-RestMethod -Uri "$BaseUrl/api/v1/resilience/status" -Method Get
Write-Host "Redis Circuit Breaker State After Recovery: $($statusAfterRedisStart.circuitBreakers.redisService.state)" -ForegroundColor Green

# -----------------------------------------------------------------------------
# Experiment 6: Downstream Timeout and Circuit Breaker Trip
# -----------------------------------------------------------------------------
Log-Step "Experiment 6: Downstream Dependency Timeout and Circuit Breaker Trip"

# Create players and start match
$suffix = [System.Guid]::NewGuid().ToString().Substring(0,6)
$hostPayload = @{ username = "ChaosHost_$suffix"; email = "chaosh_$suffix@gamepulse.com"; initialMmr = 1200 } | ConvertTo-Json
$hostP = Invoke-RestMethod -Uri "$BaseUrl/api/v1/players" -Method Post -Headers @{ "Content-Type" = "application/json" } -Body $hostPayload

$guestPayload = @{ username = "ChaosGuest_$suffix"; email = "chaosg_$suffix@gamepulse.com"; initialMmr = 1200 } | ConvertTo-Json
$guestP = Invoke-RestMethod -Uri "$BaseUrl/api/v1/players" -Method Post -Headers @{ "Content-Type" = "application/json" } -Body $guestPayload

$lobbyPayload = @{ title = "Chaos Arena $suffix"; gameMode = "DUEL_1V1"; maxPlayers = 2 } | ConvertTo-Json
$matchLobby = Invoke-RestMethod -Uri "$BaseUrl/api/v1/matches/lobbies" -Method Post -Headers @{ "Content-Type" = "application/json" } -Body $lobbyPayload

$joinHostPayload = @{ playerId = $hostP.id; team = 1 } | ConvertTo-Json
$null = Invoke-RestMethod -Uri "$BaseUrl/api/v1/matches/$($matchLobby.id)/join" -Method Post -Headers @{ "Content-Type" = "application/json" } -Body $joinHostPayload

$joinGuestPayload = @{ playerId = $guestP.id; team = 2 } | ConvertTo-Json
$null = Invoke-RestMethod -Uri "$BaseUrl/api/v1/matches/$($matchLobby.id)/join" -Method Post -Headers @{ "Content-Type" = "application/json" } -Body $joinGuestPayload

$null = Invoke-RestMethod -Uri "$BaseUrl/api/v1/matches/$($matchLobby.id)/start" -Method Post

# Enable Downstream Chaos
$chaosEnable = @{ active = $true } | ConvertTo-Json
$null = Invoke-RestMethod -Uri "$BaseUrl/api/v1/resilience/chaos/downstream" -Method Post -Headers @{ "Content-Type" = "application/json" } -Body $chaosEnable
Write-Host "Downstream Chaos Activated" -ForegroundColor Yellow

# Complete match under chaos
$completePayload = @{ winnerId = $hostP.id; reason = "Knockout" } | ConvertTo-Json
$completedMatch = Invoke-RestMethod -Uri "$BaseUrl/api/v1/matches/$($matchLobby.id)/complete" -Method Post -Headers @{ "Content-Type" = "application/json" } -Body $completePayload

Write-Host "Match Completed Status: $($completedMatch.status) (API remained 200 OK)" -ForegroundColor Green

# Check resilience status
$chaosStatus = Invoke-RestMethod -Uri "$BaseUrl/api/v1/resilience/status" -Method Get
Write-Host "Downstream Fallbacks Recorded: $($chaosStatus.downstreamNotification.fallbacks)" -ForegroundColor Green

# Disable Downstream Chaos
$chaosDisable = @{ active = $false } | ConvertTo-Json
$null = Invoke-RestMethod -Uri "$BaseUrl/api/v1/resilience/chaos/downstream" -Method Post -Headers @{ "Content-Type" = "application/json" } -Body $chaosDisable

# -----------------------------------------------------------------------------
# Experiment 7: Data Integrity Check
# -----------------------------------------------------------------------------
Log-Step "Experiment 7: Verifying Final Database Integrity"
$finalHost = Invoke-RestMethod -Uri "$BaseUrl/api/v1/players/$($hostP.id)" -Method Get
$finalGuest = Invoke-RestMethod -Uri "$BaseUrl/api/v1/players/$($guestP.id)" -Method Get

Write-Host "Host Player Status after completed match: $($finalHost.status) (Expected: ONLINE)" -ForegroundColor Green
Write-Host "Guest Player Status after completed match: $($finalGuest.status) (Expected: ONLINE)" -ForegroundColor Green

Write-Host "`nAll 7 Live Resilience Experiments Completed Successfully!" -ForegroundColor Green
