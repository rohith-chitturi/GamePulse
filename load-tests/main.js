import { sleep } from 'k6';
import { getProfileOptions } from './config/profiles.js';
import { executePlayerProfileScenario } from './scenarios/player-profile.js';
import { executeMatchmakingScenario } from './scenarios/matchmaking.js';
import { executeLobbyLifecycleScenario } from './scenarios/lobby-lifecycle.js';

// Resolve configuration from environment
const profileName = __ENV.PROFILE || 'smoke';
const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

export const options = getProfileOptions(profileName);

export default function () {
    // Traffic distribution:
    // 45% Player profile reads/writes & cache validation
    // 30% Matchmaking queue & deduplication
    // 25% Lobby lifecycle & capacity boundary enforcement
    const rand = Math.random();

    if (rand < 0.45) {
        executePlayerProfileScenario(baseUrl);
    } else if (rand < 0.75) {
        executeMatchmakingScenario(baseUrl);
    } else {
        executeLobbyLifecycleScenario(baseUrl);
    }

    // Realistic user pacing between actions
    sleep(0.1 + Math.random() * 0.2);
}

export function handleSummary(data) {
    return {
        'load-tests/results/summary.json': JSON.stringify(data, null, 2),
        stdout: textSummary(data, { indent: ' ', enableColors: true })
    };
}

function textSummary(data) {
    const httpDuration = data.metrics['http_req_duration'] ? data.metrics['http_req_duration'].values : {};
    const reqs = data.metrics['http_reqs'] ? data.metrics['http_reqs'].values : {};
    const unexpErrors = data.metrics['unexpected_http_errors'] ? data.metrics['unexpected_http_errors'].values : {};
    const cacheHits = data.metrics['redis_cache_hits_simulated'] ? data.metrics['redis_cache_hits_simulated'].values.count : 0;
    const cacheMisses = data.metrics['redis_cache_misses_simulated'] ? data.metrics['redis_cache_misses_simulated'].values.count : 0;
    const dupRejections = data.metrics['duplicate_queue_rejections'] ? data.metrics['duplicate_queue_rejections'].values.count : 0;
    const capRejections = data.metrics['lobby_capacity_rejections'] ? data.metrics['lobby_capacity_rejections'].values.count : 0;
    const idempReplays = data.metrics['idempotent_replays'] ? data.metrics['idempotent_replays'].values.count : 0;

    return `
================================================================================
                    GAMEPULSE K6 PERFORMANCE BENCHMARK
================================================================================
  Target Base URL:       ${baseUrl}
  Profile Executed:      ${profileName.toUpperCase()}
  Total Requests:        ${reqs.count || 0}
  Throughput:            ${(reqs.rate || 0).toFixed(2)} req/s
  Avg Latency:           ${(httpDuration.avg || 0).toFixed(2)} ms
  p50 Latency:           ${(httpDuration.med || 0).toFixed(2)} ms
  p90 Latency:           ${(httpDuration['p(90)'] || 0).toFixed(2)} ms
  p95 Latency:           ${(httpDuration['p(95)'] || 0).toFixed(2)} ms
  p99 Latency:           ${(httpDuration['p(99)'] || 0).toFixed(2)} ms
  Unexpected Error Rate: ${((unexpErrors.rate || 0) * 100).toFixed(2)}%

  --- Concurrency & Correctness Verifications ---
  Simulated Cache Hits:  ${cacheHits}
  Simulated Cache Misses:${cacheMisses}
  Duplicate Queue 409s:  ${dupRejections} (Concurrency Deduplication)
  Lobby Capacity 409s:   ${capRejections} (Capacity Limit Enforced)
  Idempotent Replays:    ${idempReplays} (Cache-Replay Verified)
================================================================================
`;
}

