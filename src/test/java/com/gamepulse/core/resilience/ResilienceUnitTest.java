package com.gamepulse.core.resilience;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResilienceUnitTest {

    private DownstreamNotificationService downstreamService;
    private RedisResilienceService redisResilienceService;
    private StringRedisTemplate mockRedisTemplate;
    private ValueOperations<String, String> mockValueOperations;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        downstreamService = new DownstreamNotificationService();
        objectMapper = new ObjectMapper();
        mockRedisTemplate = mock(StringRedisTemplate.class);
        mockValueOperations = mock(ValueOperations.class);
        when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOperations);

        redisResilienceService = new RedisResilienceService(Optional.of(mockRedisTemplate), objectMapper);
    }

    @Test
    @DisplayName("Resilience: Downstream notification delivers successfully during normal operation")
    void testDownstreamNotificationSuccess() {
        UUID matchId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();

        boolean result = downstreamService.notifyMatchCompleted(matchId, winnerId);

        assertThat(result).isTrue();
        assertThat(downstreamService.getInvocationCount()).isEqualTo(1);
        assertThat(downstreamService.getSuccessCount()).isEqualTo(1);
        assertThat(downstreamService.getFallbackCount()).isZero();
    }

    @Test
    @DisplayName("Resilience: Downstream fallback handler executes and logs safely without throwing")
    void testDownstreamFallbackHandlerExecution() {
        UUID matchId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();

        // Direct invocation of the fallback handler
        boolean result = downstreamService.notifyMatchCompletedFallback(matchId, winnerId, new SocketTimeoutException("Simulated timeout"));

        assertThat(result).isFalse();
        assertThat(downstreamService.getFallbackCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Resilience: Resilience4j CircuitBreaker and Retry wrap downstream call with bounded retries")
    void testResilience4jDecoratedExecution() {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("downstream");
        Retry retry = Retry.of("downstream", RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(10)).build());

        AtomicInteger callCount = new AtomicInteger(0);

        Supplier<Boolean> failingCall = () -> {
            callCount.incrementAndGet();
            throw new RuntimeException(new SocketTimeoutException("Simulated network timeout"));
        };

        Supplier<Boolean> retryingCall = Retry.decorateSupplier(retry, failingCall);
        Supplier<Boolean> protectedCall = CircuitBreaker.decorateSupplier(cb, retryingCall);

        boolean result;
        try {
            result = protectedCall.get();
        } catch (Exception e) {
            result = downstreamService.notifyMatchCompletedFallback(UUID.randomUUID(), UUID.randomUUID(), e);
        }

        assertThat(result).isFalse();
        // Bounded retry executed 3 attempts
        assertThat(callCount.get()).isEqualTo(3);
        // Fallback executed once after retries were exhausted
        assertThat(downstreamService.getFallbackCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Resilience: Circuit breaker trips from CLOSED to OPEN after failure threshold")
    void testCircuitBreakerTripsToOpenOnConsecutiveFailures() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(2))
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker cb = registry.circuitBreaker("testService");

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Record 2 failures
        cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("Downstream timeout"));
        cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("Downstream timeout"));

        // Failure rate is 100% >= 50% threshold with 2 calls >= 2 min calls -> trips to OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.getMetrics().getFailureRate()).isEqualTo(100.0f);

        // Verify transition to HALF_OPEN
        cb.transitionToHalfOpenState();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // Successful probe resets to CLOSED
        cb.onSuccess(0, java.util.concurrent.TimeUnit.MILLISECONDS);
        cb.onSuccess(0, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("Resilience: Redis get degrades gracefully to empty on connection failure")
    void testRedisGetFallbackOnConnectionFailure() {
        when(mockValueOperations.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis connection refused on port 6379"));

        // Must intercept exception via fallback and return Optional.empty()
        Optional<String> result = redisResilienceService.get("player:profile:123", String.class);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Resilience: Redis set silently degrades on connection failure without breaking write path")
    void testRedisSetFallbackOnConnectionFailure() {
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("Redis connection refused"))
                .when(mockValueOperations).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class));

        // Must not throw exception
        redisResilienceService.set("player:profile:123", "test-payload", Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("Resilience: Redis delete silently degrades when Redis is unreachable")
    void testRedisDeleteFallbackOnConnectionFailure() {
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("Redis offline"))
                .when(mockRedisTemplate).delete(anyString());

        // Must not throw exception
        redisResilienceService.delete("player:profile:123");
    }
}
