package com.ea.gamepulse;

import com.ea.gamepulse.matchmaking.dto.EnqueueRequest;
import com.ea.gamepulse.player.dto.CreatePlayerRequest;
import com.ea.gamepulse.player.dto.PlayerResponse;
import com.ea.gamepulse.session.dto.CompleteMatchRequest;
import com.ea.gamepulse.session.dto.CreateLobbyRequest;
import com.ea.gamepulse.session.dto.JoinLobbyRequest;
import com.fasterxml.jackson.databind.JsonNode;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GameplayIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("End-to-End: Matchmaking queue deterministically matches eligible players and creates active session")
    void testEndToEndMatchmakingFlow() throws Exception {
        // 1. Create two eligible players with close MMR
        PlayerResponse p1 = createPlayerViaApi("Titan_Alpha", "alpha@ea.com", 1200);
        PlayerResponse p2 = createPlayerViaApi("Titan_Bravo", "bravo@ea.com", 1240);

        // 2. Enqueue Player 1
        MvcResult enqueueResult1 = mockMvc.perform(post("/api/v1/matchmaking/queue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnqueueRequest(p1.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andReturn();

        JsonNode ticketJson1 = objectMapper.readTree(enqueueResult1.getResponse().getContentAsString());
        UUID ticketId1 = UUID.fromString(ticketJson1.get("ticketId").asText());

        // 3. Enqueue Player 2 -> triggers matchmaker, pairs p1 & p2 immediately
        MvcResult enqueueResult2 = mockMvc.perform(post("/api/v1/matchmaking/queue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnqueueRequest(p2.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("MATCHED")))
                .andExpect(jsonPath("$.matchSessionId", notNullValue()))
                .andReturn();

        JsonNode ticketJson2 = objectMapper.readTree(enqueueResult2.getResponse().getContentAsString());
        UUID matchSessionId = UUID.fromString(ticketJson2.get("matchSessionId").asText());

        // 4. Verify Ticket 1 is now MATCHED
        mockMvc.perform(get("/api/v1/matchmaking/tickets/" + ticketId1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("MATCHED")))
                .andExpect(jsonPath("$.matchSessionId", is(matchSessionId.toString())));

        // 5. Complete match
        CompleteMatchRequest completeRequest = new CompleteMatchRequest(p1.getId(), "Round 1 Knockout");
        mockMvc.perform(post("/api/v1/matches/" + matchSessionId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.winnerId", is(p1.getId().toString())));

        // 6. Verify player states reverted to ONLINE
        mockMvc.perform(get("/api/v1/players/" + p1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ONLINE")));

        mockMvc.perform(get("/api/v1/players/" + p2.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ONLINE")));
    }

    @Test
    @DisplayName("End-to-End: Custom lobby lifecycle with capacity boundary enforcement")
    void testCustomLobbyLifecycle() throws Exception {
        PlayerResponse host = createPlayerViaApi("LobbyHost", "host@ea.com", 1000);
        PlayerResponse guest = createPlayerViaApi("LobbyGuest", "guest@ea.com", 1000);
        PlayerResponse intruder = createPlayerViaApi("Intruder", "intruder@ea.com", 1000);

        // 1. Create 2-player lobby
        CreateLobbyRequest lobbyRequest = new CreateLobbyRequest("1v1 Showdown", "DUEL_1V1", 2);
        MvcResult lobbyResult = mockMvc.perform(post("/api/v1/matches/lobbies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lobbyRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("WAITING_FOR_PLAYERS")))
                .andReturn();

        JsonNode lobbyJson = objectMapper.readTree(lobbyResult.getResponse().getContentAsString());
        UUID lobbyId = UUID.fromString(lobbyJson.get("id").asText());

        // 2. Join host & guest
        mockMvc.perform(post("/api/v1/matches/" + lobbyId + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinLobbyRequest(host.getId(), 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlayers", is(1)));

        mockMvc.perform(post("/api/v1/matches/" + lobbyId + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinLobbyRequest(guest.getId(), 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlayers", is(2)));

        // 3. Attempt join 3rd player -> 409 Conflict (CAPACITY_EXCEEDED)
        mockMvc.perform(post("/api/v1/matches/" + lobbyId + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinLobbyRequest(intruder.getId(), 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("CAPACITY_EXCEEDED")));

        // 4. Start match
        mockMvc.perform(post("/api/v1/matches/" + lobbyId + "/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));

        // 5. Complete match
        mockMvc.perform(post("/api/v1/matches/" + lobbyId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CompleteMatchRequest(host.getId(), "GG"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));
    }

    @Test
    @DisplayName("OpenAPI Documentation: Verifies Swagger/OpenAPI spec endpoint is available")
    void testOpenApiDocumentationAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/players']", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/matches/lobbies']", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/matchmaking/queue']", notNullValue()));
    }

    private PlayerResponse createPlayerViaApi(String username, String email, int mmr) throws Exception {
        CreatePlayerRequest request = new CreatePlayerRequest(username, email, mmr);
        MvcResult result = mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PlayerResponse.class);
    }
}
