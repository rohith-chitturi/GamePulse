/**
 * Load testing profiles and SLA target thresholds.
 */
export const PROFILES = {
    smoke: {
        stages: [
            { duration: '5s', target: 5 },
            { duration: '20s', target: 5 },
            { duration: '5s', target: 0 }
        ]
    },
    baseline: {
        stages: [
            { duration: '20s', target: 25 },
            { duration: '80s', target: 25 },
            { duration: '20s', target: 0 }
        ]
    },
    moderate: {
        stages: [
            { duration: '30s', target: 50 },
            { duration: '2m', target: 50 },
            { duration: '30s', target: 0 }
        ]
    },
    high: {
        stages: [
            { duration: '30s', target: 100 },
            { duration: '2m', target: 100 },
            { duration: '30s', target: 0 }
        ]
    },
    stress: {
        stages: [
            { duration: '45s', target: 200 },
            { duration: '1m30s', target: 200 },
            { duration: '45s', target: 0 }
        ]
    }
};

/**
 * Standard performance target thresholds.
 */
export const DEFAULT_THRESHOLDS = {
    // p95 latency must be below 500 ms for normal requests
    'http_req_duration': ['p(95)<500', 'p(99)<1200'],
    // Profile read latency should be fast (especially with Redis cache)
    'profile_read_latency_ms': ['p(95)<250'],
    // Unexpected error rate (excluding expected 409 business responses) must remain < 1%
    'unexpected_http_errors': ['rate<0.01']
};

/**
 * Resolves stages for the active profile name.
 */
export function getProfileOptions(profileName = 'smoke') {
    const selected = PROFILES[profileName] || PROFILES.smoke;
    return {
        stages: selected.stages,
        thresholds: DEFAULT_THRESHOLDS
    };
}

