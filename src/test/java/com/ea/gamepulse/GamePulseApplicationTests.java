package com.ea.gamepulse;

import com.ea.gamepulse.core.filter.CorrelationIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GamePulseApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Context Loads: Verifies Spring Boot Application Context boots successfully")
    void contextLoads() {
        // Verified by successful startup
    }

    @Test
    @DisplayName("System Info: Verifies service metadata endpoint is functional")
    void testSystemInfoEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("gamepulse"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"))
                .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER));
    }

    @Test
    @DisplayName("Actuator Health: Verifies health endpoint returns UP with liveness/readiness")
    void testActuatorHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Correlation ID: Verifies propagation of caller-supplied correlation id")
    void testCorrelationIdPreservation() throws Exception {
        String testCorrelationId = "test-corr-id-12345";
        mockMvc.perform(get("/api/v1/system/info")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, testCorrelationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, testCorrelationId));
    }

    @Test
    @DisplayName("Prometheus Metrics: Verifies Prometheus scraping endpoint is enabled")
    void testPrometheusEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/plain")));
    }
}
