package com.ea.gamepulse.player;

import com.ea.gamepulse.player.dto.CreatePlayerRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlayerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("REST API: Create player returns 201 Created with valid payload")
    void testCreatePlayerApi() throws Exception {
        CreatePlayerRequest request = new CreatePlayerRequest("GhostRider", "ghost@ea.com", 1500);

        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.username", is("GhostRider")))
                .andExpect(jsonPath("$.email", is("ghost@ea.com")))
                .andExpect(jsonPath("$.mmr", is(1500)))
                .andExpect(jsonPath("$.status", is("ONLINE")));
    }

    @Test
    @DisplayName("REST API: Rejects invalid player payload with 400 Bad Request and validation errors")
    void testCreatePlayerValidationFailure() throws Exception {
        // Invalid username (too short) and invalid email format
        CreatePlayerRequest request = new CreatePlayerRequest("ab", "not-an-email", -50);

        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.validationErrors.username", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.email", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.initialMmr", notNullValue()));
    }

    @Test
    @DisplayName("REST API: Rejects duplicate player registration with 409 Conflict")
    void testCreatePlayerDuplicateConflict() throws Exception {
        CreatePlayerRequest request1 = new CreatePlayerRequest("Valkyrie", "valk@ea.com", 1200);
        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        CreatePlayerRequest request2 = new CreatePlayerRequest("Valkyrie", "valk2@ea.com", 1300);
        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("DUPLICATE_RESOURCE")));
    }

    @Test
    @DisplayName("REST API: List players returns paginated results")
    void testListPlayersPagination() throws Exception {
        mockMvc.perform(get("/api/v1/players?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()));
    }
}
