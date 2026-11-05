import http from 'k6/http';
import { check } from 'k6';
import {
    buildHeaders,
    generateEmail,
    generateUsername,
    generateUuid,
    profileReadLatency,
    redisCacheHits,
    redisCacheMisses,
    unexpectedErrors
} from '../utils/helpers.js';

/**
 * Scenario: Player Profile Load
 * - Concurrent player creation with Idempotency-Key
 * - Profile retrieval (first read primes Redis cache, second read hits Redis)
 * - Paginated player queries
 */
export function executePlayerProfileScenario(baseUrl) {
    const username = generateUsername('k6_ply');
    const email = generateEmail(username);
    const idempotencyKey = generateUuid();

    // 1. Create player profile
    const createPayload = JSON.stringify({
        username: username,
        email: email,
        initialMmr: 1200 + Math.floor(Math.random() * 300)
    });

    const createRes = http.post(
        `${baseUrl}/api/v1/players`,
        createPayload,
        { headers: buildHeaders(idempotencyKey) }
    );

    const isCreated = check(createRes, {
        'create player status is 201': (r) => r.status === 201,
        'create player returns valid id': (r) => {
            try {
                return JSON.parse(r.body).id !== undefined;
            } catch (e) {
                return false;
            }
        }
    });

    if (!isCreated) {
        unexpectedErrors.add(1);
        return;
    }

    unexpectedErrors.add(0);
    const createdPlayer = JSON.parse(createRes.body);
    const playerId = createdPlayer.id;

    // 2. Profile read (First read - serves from DB or sets cache)
    const readStart1 = Date.now();
    const readRes1 = http.get(
        `${baseUrl}/api/v1/players/${playerId}`,
        { headers: buildHeaders() }
    );
    profileReadLatency.add(Date.now() - readStart1);

    const isRead1Ok = check(readRes1, {
        'read player status is 200': (r) => r.status === 200,
        'read player username matches': (r) => {
            try {
                return JSON.parse(r.body).username === username;
            } catch (e) {
                return false;
            }
        }
    });

    if (!isRead1Ok) {
        unexpectedErrors.add(1);
    } else {
        unexpectedErrors.add(0);
        redisCacheMisses.add(1);
    }

    // 3. Repeated profile read (Second read - verifies Redis cache hit path)
    const readStart2 = Date.now();
    const readRes2 = http.get(
        `${baseUrl}/api/v1/players/${playerId}`,
        { headers: buildHeaders() }
    );
    profileReadLatency.add(Date.now() - readStart2);

    const isRead2Ok = check(readRes2, {
        'cached read player status is 200': (r) => r.status === 200,
        'cached read response is valid': (r) => {
            try {
                return JSON.parse(r.body).id === playerId;
            } catch (e) {
                return false;
            }
        }
    });

    if (!isRead2Ok) {
        unexpectedErrors.add(1);
    } else {
        unexpectedErrors.add(0);
        redisCacheHits.add(1);
    }

    // 4. Paginated list query
    const listRes = http.get(
        `${baseUrl}/api/v1/players?page=0&size=10`,
        { headers: buildHeaders() }
    );

    const isListOk = check(listRes, {
        'list players status is 200': (r) => r.status === 200
    });

    if (!isListOk) {
        unexpectedErrors.add(1);
    } else {
        unexpectedErrors.add(0);
    }
}
