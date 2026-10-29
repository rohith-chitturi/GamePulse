package com.gamepulse.session;

import com.gamepulse.core.idempotency.filter.IdempotencyFilter;
import com.gamepulse.matchmaking.dto.EnqueueRequest;
import com.gamepulse.matchmaking.model.TicketStatus;
import com.gamepulse.matchmaking.repository.MatchmakingTicketRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("gamepulsedb_test")
            .withUsername("pulse_admin")
            .withPassword("pulse_secret");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
            registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        }
    }

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

    @Test
    @DisplayName("PostgreSQL Container: 8 parallel joins on capacity-1 lobby boundary; exactly 1 succeeds, DB capacity strictly 2")
    void testPostgresParallelLobbyJoins() throws Exception {
        MatchSessionResponse lobby = matchSessionService.createLobby(new CreateLobbyRequest("PG Arena", "DUEL_1V1", 2));
        UUID lobbyId = lobby.getId();

        PlayerResponse host = playerService.createPlayer(new CreatePlayerRequest("PGHost", "pghost@ea.com", 1200));
        matchSessionService.joinLobby(lobbyId, new JoinLobbyRequest(host.getId(), 1));

        int threadCount = 8;
        List<PlayerResponse> contenders = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            contenders.add(playerService.createPlayer(new CreatePlayerRequest("PGContender_" + i, "pgc" + i + "@ea.com", 1200)));
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final UUID contenderId = contenders.get(i).getId();
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    MvcResult result = mockMvc.perform(post("/api/v1/matches/" + lobbyId + "/join")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new JoinLobbyRequest(contenderId, 2))))
                            .andReturn();

                    int status = result.getResponse().getStatus();
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
        startLatch.countDown();
        boolean finished = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);

        // Verify PostgreSQL database state
        MatchSession pgSession = matchSessionRepository.findById(lobbyId).orElseThrow();
        assertThat(pgSession.getPlayers()).hasSize(2);
    }

    @Test
    @DisplayName("PostgreSQL Container: Idempotency under PostgreSQL persistence prevents duplicate player inserts on retries")
    void testPostgresIdempotencyPersisted() throws Exception {
        String key = UUID.randomUUID().toString();
        CreatePlayerRequest req = new CreatePlayerRequest("PGIdempUser", "pgidemp@ea.com", 1400);
        String payload = objectMapper.writeValueAsString(req);

        // 1. First execution
        mockMvc.perform(post("/api/v1/players")
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key));

        // 2. Retry execution
        mockMvc.perform(post("/api/v1/players")
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.username", is("PGIdempUser")));

        // 3. PostgreSQL database verification
        long count = playerRepository.findAll().stream()
                .filter(p -> p.getUsername().equals("PGIdempUser"))
                .count();
        assertThat(count).isEqualTo(1);
    }
}
