package com.gamepulse.core.resilience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamepulse.core.idempotency.filter.IdempotencyFilter;
import com.gamepulse.matchmaking.repository.MatchmakingTicketRepository;
import com.gamepulse.player.dto.CreatePlayerRequest;
import com.gamepulse.player.dto.PlayerResponse;
import com.gamepulse.player.repository.PlayerRepository;
import com.gamepulse.player.service.PlayerService;
import com.gamepulse.session.dto.CompleteMatchRequest;
import com.gamepulse.session.dto.CreateLobbyRequest;
import com.gamepulse.session.dto.JoinLobbyRequest;
import com.gamepulse.session.dto.MatchSessionResponse;
import com.gamepulse.session.repository.MatchSessionRepository;
import com.gamepulse.session.service.MatchSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"gamepulse.chaos.enabled=true"})
class ResilienceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private MatchSessionService matchSessionService;

    @Autowired
    private MatchSessionRepository matchSessionRepository;

    @Autowired
    private MatchmakingTicketRepository ticketRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private DownstreamNotificationService downstreamNotificationService;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        matchSessionRepository.deleteAll();
        playerRepository.deleteAll();
        downstreamNotificationService.setChaosFaultActive(false);
        downstreamNotificationService.resetCounts();
    }

    @AfterEach
    void tearDown() {
        ticketRepository.deleteAll();
        matchSessionRepository.deleteAll();
        downstreamNotificationService.setChaosFaultActive(false);
        downstreamNotificationService.resetCounts();
    }

    @Test
    @DisplayName("Resilience: Status endpoint exposes real-time circuit breakers, cache status, and retry metrics")
    void testResilienceStatusEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/resilience/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redis", notNullValue()))
                .andExpect(jsonPath("$.circuitBreakers", notNullValue()))
                .andExpect(jsonPath("$.circuitBreakers.downstreamService", notNullValue()))
                .andExpect(jsonPath("$.circuitBreakers.downstreamService.state", notNullValue()))
                .andExpect(jsonPath("$.retries", notNullValue()))
                .andExpect(jsonPath("$.downstreamNotification", notNullValue()));
    }

    @Test
    @DisplayName("Resilience: Downstream timeout simulation activates fallback and circuit breaker protects match completion")
    void testDownstreamChaosAndCircuitBreakerTrip() throws Exception {
        // 1. Enable downstream chaos fault injection
        mockMvc.perform(post("/api/v1/resilience/chaos/downstream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaosFaultActive", is(true)));

        // 2. Create players & start a match session
        PlayerResponse p1 = playerService.createPlayer(new CreatePlayerRequest("ResilientP1", "res1@gamepulse.com", 1200));
        PlayerResponse p2 = playerService.createPlayer(new CreatePlayerRequest("ResilientP2", "res2@gamepulse.com", 1200));

        MatchSessionResponse lobby = matchSessionService.createLobby(new CreateLobbyRequest("Resilience Showdown", "DUEL_1V1", 2));
        matchSessionService.joinLobby(lobby.getId(), new JoinLobbyRequest(p1.getId(), 1));
        matchSessionService.joinLobby(lobby.getId(), new JoinLobbyRequest(p2.getId(), 2));
        matchSessionService.startMatch(lobby.getId());

        // 3. Complete match -> Downstream timeout occurs, fallback executes, API returns 200 COMPLETED
        CompleteMatchRequest completeReq = new CompleteMatchRequest(p1.getId(), "Flawless Victory");
        mockMvc.perform(post("/api/v1/matches/" + lobby.getId() + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.winnerId", is(p1.getId().toString())));

        // 4. Verify fallback was invoked in downstream service
        assertThat(downstreamNotificationService.getFallbackCount()).isGreaterThanOrEqualTo(1);

        // 5. Verify resilience status endpoint reflects the failure
        mockMvc.perform(get("/api/v1/resilience/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downstreamNotification.fallbacks", notNullValue()));

        // 6. Disable chaos and reset circuit breaker
        mockMvc.perform(post("/api/v1/resilience/chaos/downstream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaosFaultActive", is(false)));

        mockMvc.perform(post("/api/v1/resilience/circuit-breakers/downstreamService/transition/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("CLOSED")));
    }

    @Test
    @DisplayName("Resilience: Actuator Prometheus endpoint exposes Resilience4j circuit breaker metrics")
    void testPrometheusResilienceMetricsExposed() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).contains("resilience4j_circuitbreaker_state");
                });
    }

    @Test
    @DisplayName("Resilience: Idempotency replay works seamlessly across downstream fallback invocations")
    void testIdempotencyReplayAcrossDownstreamFallback() throws Exception {
        downstreamNotificationService.setChaosFaultActive(true);

        PlayerResponse p1 = playerService.createPlayer(new CreatePlayerRequest("IdempRes1", "idempres1@gamepulse.com", 1200));
        PlayerResponse p2 = playerService.createPlayer(new CreatePlayerRequest("IdempRes2", "idempres2@gamepulse.com", 1200));

        MatchSessionResponse lobby = matchSessionService.createLobby(new CreateLobbyRequest("Idemp Res Arena", "DUEL_1V1", 2));
        matchSessionService.joinLobby(lobby.getId(), new JoinLobbyRequest(p1.getId(), 1));
        matchSessionService.joinLobby(lobby.getId(), new JoinLobbyRequest(p2.getId(), 2));
        matchSessionService.startMatch(lobby.getId());

        String idempotencyKey = UUID.randomUUID().toString();
        CompleteMatchRequest completeReq = new CompleteMatchRequest(p1.getId(), "Idempotent Win");
        String payload = objectMapper.writeValueAsString(completeReq);

        // First attempt (downstream in fallback mode) -> 200 OK
        MvcResult res1 = mockMvc.perform(post("/api/v1/matches/" + lobby.getId() + "/complete")
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andReturn();

        // Retry attempt with identical key -> Replays cached 200 OK with Idempotency-Replayed header
        MvcResult res2 = mockMvc.perform(post("/api/v1/matches/" + lobby.getId() + "/complete")
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andReturn();

        JsonNode json1 = objectMapper.readTree(res1.getResponse().getContentAsString());
        JsonNode json2 = objectMapper.readTree(res2.getResponse().getContentAsString());
        assertThat(json1.get("id").asText()).isEqualTo(json2.get("id").asText());
        assertThat(json1.get("winnerId").asText()).isEqualTo(json2.get("winnerId").asText());
    }
}
