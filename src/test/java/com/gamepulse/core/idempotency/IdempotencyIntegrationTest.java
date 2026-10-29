package com.gamepulse.core.idempotency;

import com.gamepulse.core.idempotency.filter.IdempotencyFilter;
import com.gamepulse.core.idempotency.repository.IdempotencyRecordRepository;
import com.gamepulse.player.dto.CreatePlayerRequest;
import com.gamepulse.player.dto.PlayerResponse;
import com.gamepulse.player.repository.PlayerRepository;
import com.gamepulse.session.dto.CreateLobbyRequest;
import com.gamepulse.session.repository.MatchSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private MatchSessionRepository matchSessionRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Test
    @DisplayName("Idempotency: Repeated POST with same key & payload returns cached response without duplicate DB insert")
    void testRepeatedRequestReturnsCachedResponse() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        CreatePlayerRequest request = new CreatePlayerRequest("IdempUser1", "idemp1@ea.com", 1300);
        String payload = objectMapper.writeValueAsString(request);

        // 1. First execution -> 201 Created
        MvcResult firstResult = mockMvc.perform(post("/api/v1/players")
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.username", is("IdempUser1")))
                .andReturn();

        PlayerResponse firstResponse = objectMapper.readValue(firstResult.getResponse().getContentAsString(), PlayerResponse.class);

        // 2. Retry execution with identical key & payload -> Returns cached 201 Created with Idempotency-Replayed header
        MvcResult secondResult = mockMvc.perform(post("/api/v1/players")
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey))
                .andExpect(header().string(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.id", is(firstResponse.getId().toString())))
                .andExpect(jsonPath("$.username", is("IdempUser1")))
                .andReturn();

        PlayerResponse secondResponse = objectMapper.readValue(secondResult.getResponse().getContentAsString(), PlayerResponse.class);

        // Verify final database state: exactly ONE player record with username "IdempUser1" exists
        assertThat(firstResponse.getId()).isEqualTo(secondResponse.getId());
        long count = playerRepository.findAll().stream()
                .filter(p -> p.getUsername().equals("IdempUser1"))
                .count();
        assertThat(count).isEqualTo(1);

        // Verify idempotency record persisted in database
        assertThat(idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)).isPresent();
    }

    @Test
    @DisplayName("Idempotency: Conflicting reuse with different payload returns 409 Conflict")
    void testConflictingKeyReuseRejected() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        CreatePlayerRequest request1 = new CreatePlayerRequest("PayloadOne", "p1@idemp.com", 1100);
        CreatePlayerRequest request2 = new CreatePlayerRequest("PayloadTwo", "p2@idemp.com", 1500);

        // 1. First execution succeeds
        mockMvc.perform(post("/api/v1/players")
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // 2. Second request with same key but different payload -> 409 Conflict
        mockMvc.perform(post("/api/v1/players")
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("IDEMPOTENCY_KEY_PAYLOAD_MISMATCH")));

        // Verify PayloadTwo was NEVER created in the database
        assertThat(playerRepository.existsByUsernameIgnoreCase("PayloadTwo")).isFalse();
    }

    @Test
    @DisplayName("Idempotency: Lobby creation idempotency prevents duplicate lobby creation on network retries")
    void testLobbyCreationIdempotency() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        CreateLobbyRequest lobbyRequest = new CreateLobbyRequest("Competitive Queue", "DUEL_1V1", 2);
        String payload = objectMapper.writeValueAsString(lobbyRequest);

        // First attempt
        MvcResult res1 = mockMvc.perform(post("/api/v1/matches/lobbies")
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        // Retry attempt
        MvcResult res2 = mockMvc.perform(post("/api/v1/matches/lobbies")
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER, "true"))
                .andReturn();

        // Both responses must contain the identical lobby ID
        String lobbyId1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("id").asText();
        String lobbyId2 = objectMapper.readTree(res2.getResponse().getContentAsString()).get("id").asText();
        assertThat(lobbyId1).isEqualTo(lobbyId2);

        // Database must contain only ONE lobby with this title
        long lobbiesCount = matchSessionRepository.findAll().stream()
                .filter(s -> s.getTitle().equals("Competitive Queue"))
                .count();
        assertThat(lobbiesCount).isEqualTo(1);
    }
}

