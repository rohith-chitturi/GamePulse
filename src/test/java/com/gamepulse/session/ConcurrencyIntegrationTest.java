package com.gamepulse.session;

import com.gamepulse.matchmaking.dto.EnqueueRequest;
import com.gamepulse.matchmaking.model.MatchmakingTicket;
import com.gamepulse.matchmaking.model.TicketStatus;
import com.gamepulse.matchmaking.repository.MatchmakingTicketRepository;
import com.gamepulse.matchmaking.service.MatchmakingService;
import com.gamepulse.player.dto.CreatePlayerRequest;
import com.gamepulse.player.dto.PlayerResponse;
import com.gamepulse.player.repository.PlayerRepository;
import com.gamepulse.player.service.PlayerService;
import com.gamepulse.session.dto.CreateLobbyRequest;
import com.gamepulse.session.dto.JoinLobbyRequest;
import com.gamepulse.session.dto.MatchSessionResponse;
import com.gamepulse.session.model.MatchSession;
import com.gamepulse.session.repository.MatchSessionRepository;
import com.gamepulse.session.service.MatchSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class ConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MatchSessionRepository matchSessionRepository;

    @Autowired
    private MatchmakingTicketRepository ticketRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private MatchSessionService matchSessionService;

    @Autowired
    private MatchmakingService matchmakingService;

    @Test
    @DisplayName("Concurrency: 10 parallel joins for 1 remaining lobby slot; exactly 1 succeeds, DB capacity never exceeded")
    void testParallelLobbyJoinsAtCapacityBoundary() throws Exception {
        // 1. Create a 2-player lobby
        MatchSessionResponse lobby = matchSessionService.createLobby(new CreateLobbyRequest("Duel Arena", "DUEL_1V1", 2));
        UUID lobbyId = lobby.getId();

        // 2. Add Host to lobby (capacity now 1/2)
        PlayerResponse host = playerService.createPlayer(new CreatePlayerRequest("HostPlayer", "host@race.com", 1200));
        matchSessionService.joinLobby(lobbyId, new JoinLobbyRequest(host.getId(), 1));

        // 3. Create 10 contender players
        int threadCount = 10;
        List<PlayerResponse> contenders = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            contenders.add(playerService.createPlayer(new CreatePlayerRequest("Contender_" + i, "c" + i + "@race.com", 1200)));
        }

        // 4. Fire 10 simultaneous join requests
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final UUID contenderId = contenders.get(i).getId();
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Simultaneous release
                    MvcResult result = mockMvc.perform(post("/api/v1/matches/" + lobbyId + "/join")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new JoinLobbyRequest(contenderId, 2))))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    statusCodes.add(status);
                    if (status == 200) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Release threads simultaneously
        boolean finished = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);

        // 5. Authoritative database state verification
        MatchSession finalSession = matchSessionRepository.findById(lobbyId).orElseThrow();
        assertThat(finalSession.getPlayers()).hasSize(2);
        assertThat(finalSession.getPlayers().size()).isLessThanOrEqualTo(finalSession.getMaxPlayers());
    }

    @Test
    @DisplayName("Concurrency: Simultaneous duplicate enqueue requests for same player create exactly ONE ticket in DB")
    void testConcurrentDuplicateQueueRequests() throws Exception {
        PlayerResponse player = playerService.createPlayer(new CreatePlayerRequest("SpamQueuePlayer", "spam@queue.com", 1250));
        UUID playerId = player.getId();

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Simultaneous burst
                    MvcResult result = mockMvc.perform(post("/api/v1/matchmaking/queue")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new EnqueueRequest(playerId))))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409 || status == 400) {
                        rejectedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean finished = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(rejectedCount.get()).isEqualTo(threadCount - 1);

        // Database verification: Exactly 1 active ticket in the database for this player
        long activeTickets = ticketRepository.findAll().stream()
                .filter(t -> t.getPlayerId().equals(playerId) && t.getStatus() == TicketStatus.QUEUED)
                .count();
        assertThat(activeTickets).isEqualTo(1);
    }
}

