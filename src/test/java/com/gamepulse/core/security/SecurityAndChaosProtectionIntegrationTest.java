package com.gamepulse.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"gamepulse.chaos.enabled=false"})
class SecurityAndChaosProtectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should return 403 Forbidden when chaos routes are called and chaos is disabled")
    void testChaosEndpointsDisabledByDefault() throws Exception {
        mockMvc.perform(post("/api/v1/resilience/chaos/downstream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("CHAOS_DISABLED")))
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.message", containsString("Chaos fault injection is disabled")));

        mockMvc.perform(post("/api/v1/resilience/circuit-breakers/downstreamService/transition/open"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("CHAOS_DISABLED")))
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.message", containsString("circuit breaker state transitions are disabled")));
    }

    @Test
    @DisplayName("Should report chaosEnabled as false in resilience status")
    void testResilienceStatusShowsChaosDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/resilience/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaosEnabled", is(false)));
    }

    @Test
    @DisplayName("Should apply standard security headers to API responses")
    void testSecurityHeadersEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-XSS-Protection", "0"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "geolocation=(), camera=(), microphone=()"))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
    }

    @Test
    @DisplayName("Should apply no-cache headers to /api routes")
    void testApiCacheHeadersEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"));
    }
}
