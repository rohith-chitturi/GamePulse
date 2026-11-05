import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics to measure granular performance
export const unexpectedErrors = new Rate('unexpected_http_errors');
export const profileReadLatency = new Trend('profile_read_latency_ms', true);
export const redisCacheHits = new Counter('redis_cache_hits_simulated');
export const redisCacheMisses = new Counter('redis_cache_misses_simulated');
export const duplicateQueueRejections = new Counter('duplicate_queue_rejections');
export const lobbyCapacityRejections = new Counter('lobby_capacity_rejections');
export const idempotentReplays = new Counter('idempotent_replays');

/**
 * Generates an RFC 4122 version 4 compliant UUID.
 */
export function generateUuid() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        const r = (Math.random() * 16) | 0;
        const v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}

/**
 * Generates a unique username for load testing to prevent unique constraint collisions
 * while strictly adhering to the 3-30 character bean validation constraint.
 */
export function generateUsername(prefix = 'player') {
    const vu = typeof __VU !== 'undefined' ? __VU : 0;
    const iter = typeof __ITER !== 'undefined' ? __ITER : 0;
    const rand = Math.random().toString(36).substring(2, 7);
    const shortPrefix = prefix.length > 5 ? prefix.substring(0, 5) : prefix;
    return `${shortPrefix}_${vu}_${iter}_${rand}`;
}

/**
 * Generates a valid email from username.
 */
export function generateEmail(username) {
    return `${username}@gamepulse.com`;
}

/**
 * Builds HTTP headers with optional correlation and idempotency keys.
 */
export function buildHeaders(idempotencyKey = null, correlationId = null) {
    const headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'X-Correlation-Id': correlationId || generateUuid()
    };
    if (idempotencyKey) {
        headers['Idempotency-Key'] = idempotencyKey;
    }
    return headers;
}
