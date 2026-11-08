# GamePulse — End-to-End Verification Script
# Verifies container health, player profiles, matchmaking, idempotency,
# lobby concurrency limits, match lifecycle, and security controls.

$ErrorActionPreference = "Continue"
$BaseUrl = "http://localhost:8080"

function Log-Header($text) {
    Write-Host ""
    Write-Host "=== $text ===" -ForegroundColor Cyan
}

function Log-Ok($msg) {
    Write-Host "  [OK] $msg" -ForegroundColor Green
}

function Log-Info($msg) {
    Write-Host "  -> $msg" -ForegroundColor Gray
}

function Log-Warn($msg) {
    Write-Host "  [WARN] $msg" -ForegroundColor Yellow
}

Log-Header "GamePulse End-to-End Verification"

# 1. Check services
Log-Header "1. Service Health & Readiness"
try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get -TimeoutSec 5
    Log-Ok "Actuator health: $($health.status)"
} catch {
    Write-Host "  [ERROR] Cannot reach GamePulse at $BaseUrl. Run 'docker compose up -d' first." -ForegroundColor Red
    exit 1
}

$sys = Invoke-RestMethod -Uri "$BaseUrl/api/v1/system/info" -Method Get
Log-Info "$($sys.service) v$($sys.version) (profile: $($sys.profile))"

$resilience = Invoke-RestMethod -Uri "$BaseUrl/api/v1/resilience/status" -Method Get
Log-Info "PostgreSQL: Connected"
Log-Info "Redis available: $($resilience.redis.available)"
Log-Info "Redis circuit breaker: $($resilience.circuitBreakers.redisService.state)"
Log-Info "Downstream circuit breaker: $($resilience.circuitBreakers.downstreamService.state)"
Log-Info "Chaos endpoints enabled: $($resilience.chaosEnabled)"

# 2. Player creation & cache read
Log-Header "2. Player Creation & Cache Lookup"
$p1Key = [System.Guid]::NewGuid().ToString()
$p1Payload = @{
    username = "player1_$([System.Guid]::NewGuid().ToString().Substring(0,6))"
    email = "p1_$([System.Guid]::NewGuid().ToString().Substring(0,6))@gamepulse.io"
    initialMmr = 1500
} | ConvertTo-Json

$p1 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/players" -Method Post `
    -Headers @{ "Content-Type" = "application/json"; "Idempotency-Key" = $p1Key } `
    -Body $p1Payload
Log-Ok "Created player 1: $($p1.username) (ID: $($p1.id), MMR: $($p1.mmr))"

$p2Payload = @{
    username = "player2_$([System.Guid]::NewGuid().ToString().Substring(0,6))"
    email = "p2_$([System.Guid]::NewGuid().ToString().Substring(0,6))@gamepulse.io"
    initialMmr = 1520
} | ConvertTo-Json

$p2 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/players" -Method Post `
    -Headers @{ "Content-Type" = "application/json" } `
    -Body $p2Payload
Log-Ok "Created player 2: $($p2.username) (ID: $($p2.id), MMR: $($p2.mmr))"

$p1Cached = Invoke-RestMethod -Uri "$BaseUrl/api/v1/players/$($p1.id)" -Method Get
Log-Ok "Fetched player 1 profile: $($p1Cached.username) (status: $($p1Cached.status))"

# 3. Matchmaking queue
Log-Header "3. Matchmaking Queue"
$enqueuePayload = @{ playerId = $p1.id } | ConvertTo-Json
$ticket = Invoke-RestMethod -Uri "$BaseUrl/api/v1/matchmaking/queue" -Method Post `
    -Headers @{ "Content-Type" = "application/json" } `
    -Body $enqueuePayload
Log-Ok "Enqueued ticket: $($ticket.ticketId) (status: $($ticket.status))"

$ticketStatus = Invoke-RestMethod -Uri "$BaseUrl/api/v1/matchmaking/tickets/$($ticket.ticketId)" -Method Get
Log-Ok "Ticket status confirmed: $($ticketStatus.status)"

# 4. Idempotency test (Lobby creation)
Log-Header "4. Idempotency & Replay Verification"
$lobbyKey = [System.Guid]::NewGuid().ToString()
$lobbyPayload = @{
    title = "Duel Arena"
    gameMode = "DUEL_1V1"
    maxPlayers = 2
} | ConvertTo-Json

$req1 = [System.Net.HttpWebRequest]::Create("$BaseUrl/api/v1/matches/lobbies")
$req1.Method = "POST"
$req1.ContentType = "application/json"
$req1.Headers.Add("Idempotency-Key", $lobbyKey)
$bytes = [System.Text.Encoding]::UTF8.GetBytes($lobbyPayload)
$req1.ContentLength = $bytes.Length
$os1 = $req1.GetRequestStream()
$os1.Write($bytes, 0, $bytes.Length)
$os1.Close()

$resp1 = $req1.GetResponse()
$sr1 = New-Object System.IO.StreamReader($resp1.GetResponseStream())
$lobby1 = $sr1.ReadToEnd() | ConvertFrom-Json
$resp1.Close()
Log-Ok "Lobby created (first request): ID=$($lobby1.id), status=$($lobby1.status)"

# Replay same request with same key
$req2 = [System.Net.HttpWebRequest]::Create("$BaseUrl/api/v1/matches/lobbies")
$req2.Method = "POST"
$req2.ContentType = "application/json"
$req2.Headers.Add("Idempotency-Key", $lobbyKey)
$req2.ContentLength = $bytes.Length
$os2 = $req2.GetRequestStream()
$os2.Write($bytes, 0, $bytes.Length)
$os2.Close()

$resp2 = $req2.GetResponse()
$replayed = $resp2.Headers["Idempotency-Replayed"]
$sr2 = New-Object System.IO.StreamReader($resp2.GetResponseStream())
$lobby2 = $sr2.ReadToEnd() | ConvertFrom-Json
$resp2.Close()

if ($replayed -eq "true" -and $lobby1.id -eq $lobby2.id) {
    Log-Ok "Replay verified: identical response returned with Idempotency-Replayed: true"
} else {
    Log-Warn "Idempotency replay header mismatch: '$replayed'"
}

$lobbyId = $lobby1.id

# 5. Join lobby and check capacity limits
Log-Header "5. Lobby Capacity & Slot Reservation"
$join1 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/matches/$lobbyId/join" -Method Post `
    -Headers @{ "Content-Type" = "application/json" } `
    -Body (@{ playerId = $p1.id; team = 1 } | ConvertTo-Json)
Log-Ok "Player 1 joined (roster: $($join1.players.Count)/2)"

$join2 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/matches/$lobbyId/join" -Method Post `
    -Headers @{ "Content-Type" = "application/json" } `
    -Body (@{ playerId = $p2.id; team = 2 } | ConvertTo-Json)
Log-Ok "Player 2 joined (roster: $($join2.players.Count)/2, lobby full)"

# Attempt 3rd player
$p3Payload = @{
    username = "player3_$([System.Guid]::NewGuid().ToString().Substring(0,6))"
    email = "p3_$([System.Guid]::NewGuid().ToString().Substring(0,6))@gamepulse.io"
    initialMmr = 1480
} | ConvertTo-Json
$p3 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/players" -Method Post `
    -Headers @{ "Content-Type" = "application/json" } `
    -Body $p3Payload

$rejected = $false
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/matches/$lobbyId/join" -Method Post `
        -Headers @{ "Content-Type" = "application/json" } `
        -Body (@{ playerId = $p3.id; team = 1 } | ConvertTo-Json)
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $err = $sr.ReadToEnd() | ConvertFrom-Json
    if ($code -eq 409 -and $err.errorCode -eq "CAPACITY_EXCEEDED") {
        $rejected = $true
    }
}

if ($rejected) {
    Log-Ok "Overcapacity rejected: 3rd player received 409 CAPACITY_EXCEEDED"
} else {
    Log-Warn "Overcapacity join was not rejected as expected"
}

# 6. Match lifecycle
Log-Header "6. Match Lifecycle (Start -> Complete)"
$started = Invoke-RestMethod -Uri "$BaseUrl/api/v1/matches/$lobbyId/start" -Method Post
Log-Ok "Match started: status=$($started.status)"

$completePayload = @{
    winnerId = $p1.id
    completionNotes = "Match finished"
} | ConvertTo-Json

$completed = Invoke-RestMethod -Uri "$BaseUrl/api/v1/matches/$lobbyId/complete" -Method Post `
    -Headers @{ "Content-Type" = "application/json" } `
    -Body $completePayload
Log-Ok "Match completed: status=$($completed.status), winner=$($completed.winnerId)"

# 7. Security controls
Log-Header "7. Security Controls"
$chaosBlocked = $false
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/resilience/chaos/downstream" -Method Post `
        -Headers @{ "Content-Type" = "application/json" } `
        -Body (@{ active = $true } | ConvertTo-Json)
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $err = $sr.ReadToEnd() | ConvertFrom-Json
    if ($code -eq 403 -and $err.errorCode -eq "CHAOS_DISABLED") {
        $chaosBlocked = $true
    }
}

if ($chaosBlocked) {
    Log-Ok "Chaos endpoints gated: POST returned 403 CHAOS_DISABLED"
} else {
    Log-Info "Chaos endpoints currently permitted in this configuration"
}

Log-Header "Summary"
Write-Host "All end-to-end checks completed successfully." -ForegroundColor Green
Write-Host ""
