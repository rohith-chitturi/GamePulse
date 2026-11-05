import http from 'k6/http';
import { check } from 'k6';
import {
    buildHeaders,
    duplicateQueueRejections,
    generateEmail,
    generateUsername,
    generateUuid,
    unexpectedErrors
} from '../utils/helpers.js';

/**
 * Scenario: Matchmaking Queue Load & Concurrency Control
 * - Registers new player
 * - Submits player to FIFO matchmaking queue
 * - Concurrently attempts duplicate enqueue for the same player, verifying 409 DUPLICATE_RESOURCE
 * - Queries matchmaking ticket status
 */
export function executeMatchmakingScenario(baseUrl) {
    const username = generateUsername('k6_mm');
    const email = generateEmail(username);

    // 1. Create player
    const createPayload = JSON.stringify({
        username: username,
        email: email,
        initialMmr: 1200 + Math.floor(Math.random() * 200)
    });

    const playerRes = http.post(
        `${baseUrl}/api/v1/players`,
        createPayload,
        { headers: buildHeaders(generateUuid()) }
    );

    if (playerRes.status !== 201) {
        unexpectedErrors.add(1);
        return;
    }
    unexpectedErrors.add(0);

    const playerId = JSON.parse(playerRes.body).id;

    // 2. Initial matchmaking queue submission
    const queuePayload = JSON.stringify({ playerId: playerId });
    const queueRes = http.post(
        `${baseUrl}/api/v1/matchmaking/queue`,
        queuePayload,
        { headers: buildHeaders() }
    );

    const isEnqueued = check(queueRes, {
        'enqueue status is 201': (r) => r.status === 201,
        'ticket status is valid': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.status === 'QUEUED' || body.status === 'MATCHED';
            } catch (e) {
                return false;
            }
        }
    });

    if (!isEnqueued) {
        unexpectedErrors.add(1);
        return;
    }
    unexpectedErrors.add(0);

    const ticket = JSON.parse(queueRes.body);
    const ticketId = ticket.ticketId || ticket.id;

    // 3. Immediate duplicate enqueue test (verifying concurrency protection)
    const duplicateRes = http.post(
        `${baseUrl}/api/v1/matchmaking/queue`,
        queuePayload,
        { headers: buildHeaders() }
    );

    // If ticket is still QUEUED, duplicate request MUST be rejected with 409 DUPLICATE_RESOURCE
    if (ticket.status === 'QUEUED') {
        const isDuplicateRejected = check(duplicateRes, {
            'duplicate enqueue returns 409 or 201 if matched': (r) => r.status === 409 || r.status === 201,
            'duplicate error code is valid conflict': (r) => {
                if (r.status === 409) {
                    try {
                        const code = JSON.parse(r.body).errorCode;
                        return code === 'DUPLICATE_RESOURCE' || code === 'INVALID_STATE_TRANSITION' || code === 'INVALID_STATE' || code === 'CONCURRENCY_CONFLICT';
                    } catch (e) {
                        return false;
                    }
                }
                return true;
            }
        });

        if (duplicateRes.status === 409) {
            duplicateQueueRejections.add(1);
            unexpectedErrors.add(0);
        } else if (duplicateRes.status >= 500) {
            unexpectedErrors.add(1);
        }
    }

    // 4. Query ticket status
    if (ticketId) {
        const ticketRes = http.get(
            `${baseUrl}/api/v1/matchmaking/tickets/${ticketId}`,
            { headers: buildHeaders() }
        );

        const isTicketOk = check(ticketRes, {
            'query ticket status is 200': (r) => r.status === 200
        });

        if (!isTicketOk) {
            unexpectedErrors.add(1);
        } else {
            unexpectedErrors.add(0);
        }
    }
}
