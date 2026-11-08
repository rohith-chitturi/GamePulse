package com.gamepulse.core.resilience;

import com.gamepulse.core.exception.ChaosDisabledException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/resilience")
@Tag(name = "Resilience", description = "Circuit breaker inspection and fault injection endpoints")
public class ResilienceController {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final DownstreamNotificationService downstreamNotificationService;
    private final RedisResilienceService redisResilienceService;
    private final boolean chaosEnabled;

    public ResilienceController(CircuitBreakerRegistry circuitBreakerRegistry,
                                RetryRegistry retryRegistry,
                                DownstreamNotificationService downstreamNotificationService,
                                RedisResilienceService redisResilienceService,
                                @Value("${gamepulse.chaos.enabled:false}") boolean chaosEnabled) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.downstreamNotificationService = downstreamNotificationService;
        this.redisResilienceService = redisResilienceService;
        this.chaosEnabled = chaosEnabled;
    }

    @GetMapping("/status")
    @Operation(summary = "Get current circuit breaker, cache, and downstream status")
    public ResponseEntity<Map<String, Object>> getResilienceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("chaosEnabled", chaosEnabled);

        Map<String, Object> redisStatus = new HashMap<>();
        redisStatus.put("available", redisResilienceService.isRedisAvailable());
        status.put("redis", redisStatus);

        Map<String, Object> circuitBreakers = new HashMap<>();
        for (CircuitBreaker cb : circuitBreakerRegistry.getAllCircuitBreakers()) {
            Map<String, Object> cbData = new HashMap<>();
            cbData.put("state", cb.getState().name());
            cbData.put("failureRate", cb.getMetrics().getFailureRate());
            cbData.put("numberOfSuccessfulCalls", cb.getMetrics().getNumberOfSuccessfulCalls());
            cbData.put("numberOfFailedCalls", cb.getMetrics().getNumberOfFailedCalls());
            cbData.put("numberOfNotPermittedCalls", cb.getMetrics().getNumberOfNotPermittedCalls());
            circuitBreakers.put(cb.getName(), cbData);
        }
        status.put("circuitBreakers", circuitBreakers);

        Map<String, Object> retries = new HashMap<>();
        for (Retry r : retryRegistry.getAllRetries()) {
            Map<String, Object> rData = new HashMap<>();
            rData.put("successfulCallsWithRetry", r.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt());
            rData.put("failedCallsWithRetry", r.getMetrics().getNumberOfFailedCallsWithRetryAttempt());
            rData.put("successfulCallsWithoutRetry", r.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt());
            retries.put(r.getName(), rData);
        }
        status.put("retries", retries);

        Map<String, Object> downstream = new HashMap<>();
        downstream.put("chaosFaultActive", downstreamNotificationService.isChaosFaultActive());
        downstream.put("invocations", downstreamNotificationService.getInvocationCount());
        downstream.put("successes", downstreamNotificationService.getSuccessCount());
        downstream.put("fallbacks", downstreamNotificationService.getFallbackCount());
        status.put("downstreamNotification", downstream);

        return ResponseEntity.ok(status);
    }

    @PostMapping("/chaos/downstream")
    @Operation(summary = "Toggle simulated downstream timeout for failure testing")
    public ResponseEntity<Map<String, Object>> toggleDownstreamChaos(@RequestBody Map<String, Boolean> payload) {
        if (!chaosEnabled) {
            throw new ChaosDisabledException("Chaos fault injection is disabled in this environment.");
        }

        boolean active = Boolean.TRUE.equals(payload.get("active"));
        downstreamNotificationService.setChaosFaultActive(active);

        Map<String, Object> response = new HashMap<>();
        response.put("chaosFaultActive", active);
        response.put("message", active ? "Downstream timeout simulation enabled" : "Downstream timeout simulation disabled");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/circuit-breakers/{name}/transition/{action}")
    @Operation(summary = "Manually transition a circuit breaker state")
    public ResponseEntity<Map<String, Object>> transitionCircuitBreaker(
            @PathVariable String name,
            @PathVariable String action) {
        if (!chaosEnabled) {
            throw new ChaosDisabledException("Manual circuit breaker state transitions are disabled in this environment.");
        }

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
        switch (action.toLowerCase()) {
            case "open" -> cb.transitionToOpenState();
            case "close" -> cb.transitionToClosedState();
            case "half-open" -> cb.transitionToHalfOpenState();
            case "reset" -> cb.reset();
            default -> throw new IllegalArgumentException("Unknown transition action: " + action + ". Supported: open, close, half-open, reset");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("circuitBreaker", name);
        response.put("state", cb.getState().name());
        return ResponseEntity.ok(response);
    }
}
