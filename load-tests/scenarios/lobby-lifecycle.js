import http from 'k6/http';
import { check } from 'k6';
import {
    buildHeaders,
    generateEmail,
    generateUsername,
    generateUuid,
    idempotentReplays,
    lobbyCapacityRejections,
    unexpectedErrors
} from '../utils/helpers.js';

/**
 * Scenario: Lobby Lifecycle, Capacity Enforcement & Idempotency
 * - Creates 2-player custom lobby with Idempotency-Key
 * - Replays identical creation request, asserting cached 201 with Idempotency-Replayed header
 * - Registers 3 players
 * - Player 1 joins (1/2)
 * - Player 2 joins (2/2)
 * - Player 3 attempts to join: asserts 409 Conflict CAPACITY_EXCEEDED
 * - Starts match session
 * - Completes match session with winner
 */
export function executeLobbyLifecycleScenario(baseUrl) {
    const idempotencyKey = generateUuid();
    const lobbyPayload = JSON.stringify({
        title: `Lobby_${generateUsername('c')}`,
        gameMode: 'DUEL_1V1',
        maxPlayers: 2
    });

    // 1. Create 2-player lobby
    const createRes = http.post(
        `${baseUrl}/api/v1/matches/lobbies`,
        lobbyPayload,
        { headers: buildHeaders(idempotencyKey) }
    );

    const isLobbyCreated = check(createRes, {
        'lobby created status is 201': (r) => r.status === 201,
        'lobby has valid id': (r) => {
            try {
                return JSON.parse(r.body).id !== undefined;
            } catch (e) {
                return false;
            }
        }
    });

    if (!isLobbyCreated) {
        unexpectedErrors.add(1);
        return;
    }
    unexpectedErrors.add(0);

    const lobby = JSON.parse(createRes.body);
    const lobbyId = lobby.id;

    // 2. Idempotent replay of lobby creation
    const replayRes = http.post(
        `${baseUrl}/api/v1/matches/lobbies`,
        lobbyPayload,
        { headers: buildHeaders(idempotencyKey) }
    );

    const isReplayed = check(replayRes, {
        'replayed lobby status is 201': (r) => r.status === 201,
        'replayed header present': (r) => r.headers['Idempotency-Replayed'] === 'true'
    });

    if (isReplayed) {
        idempotentReplays.add(1);
        unexpectedErrors.add(0);
    } else {
        unexpectedErrors.add(1);
    }

    // Helper to register a player
    function createPlayer(tag) {
        const u = generateUsername(tag);
        const res = http.post(
            `${baseUrl}/api/v1/players`,
            JSON.stringify({ username: u, email: generateEmail(u), initialMmr: 1250 }),
            { headers: buildHeaders(generateUuid()) }
        );
        if (res.status === 201) {
            return JSON.parse(res.body).id;
        }
        return null;
    }

    const player1Id = createPlayer('p1');
    const player2Id = createPlayer('p2');
    const player3Id = createPlayer('p3');

    if (!player1Id || !player2Id || !player3Id) {
        unexpectedErrors.add(1);
        return;
    }

    // 3. Player 1 joins (Slot 1/2)
    const join1Res = http.post(
        `${baseUrl}/api/v1/matches/${lobbyId}/join`,
        JSON.stringify({ playerId: player1Id, team: 1 }),
        { headers: buildHeaders() }
    );

    check(join1Res, {
        'player 1 join status is 200': (r) => r.status === 200
    });

    // 4. Player 2 joins (Slot 2/2 -> Lobby full)
    const join2Res = http.post(
        `${baseUrl}/api/v1/matches/${lobbyId}/join`,
        JSON.stringify({ playerId: player2Id, team: 2 }),
        { headers: buildHeaders() }
    );

    check(join2Res, {
        'player 2 join status is 200': (r) => r.status === 200
    });

    // 5. Player 3 attempts to join full lobby (Capacity Enforcement)
    const join3Res = http.post(
        `${baseUrl}/api/v1/matches/${lobbyId}/join`,
        JSON.stringify({ playerId: player3Id, team: 1 }),
        { headers: buildHeaders() }
    );

    const isCapacityProtected = check(join3Res, {
        'player 3 join rejected with 409 Conflict': (r) => r.status === 409,
        'error code is CAPACITY_EXCEEDED': (r) => {
            try {
                return JSON.parse(r.body).errorCode === 'CAPACITY_EXCEEDED';
            } catch (e) {
                return false;
            }
        }
    });

    if (join3Res.status === 409) {
        lobbyCapacityRejections.add(1);
        unexpectedErrors.add(0);
    } else {
        unexpectedErrors.add(1);
    }

    // 6. Start match session
    const startRes = http.post(
        `${baseUrl}/api/v1/matches/${lobbyId}/start`,
        null,
        { headers: buildHeaders() }
    );

    check(startRes, {
        'match started status is 200': (r) => r.status === 200
    });

    // 7. Complete match session
    const completeRes = http.post(
        `${baseUrl}/api/v1/matches/${lobbyId}/complete`,
        JSON.stringify({ winnerId: player1Id }),
        { headers: buildHeaders() }
    );

    check(completeRes, {
        'match completed status is 200': (r) => r.status === 200
    });
}

