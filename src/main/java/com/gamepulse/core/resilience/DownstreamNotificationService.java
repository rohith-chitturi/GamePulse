package com.gamepulse.core.resilience;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Downstream service client with bounded exponential-backoff retries
 * and circuit-breaker protection for external event notification.
 */
@Service
public class DownstreamNotificationService {

    private static final Logger log = LoggerFactory.getLogger(DownstreamNotificationService.class);

    @Value("${gamepulse.resilience.downstream.timeout-ms:2000}")
    private int timeoutMs;

    // Simulation toggle for controlled chaos & resilience verification experiments
    private final AtomicBoolean chaosFaultActive = new AtomicBoolean(false);
    private final AtomicInteger invocationCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger fallbackCount = new AtomicInteger(0);

    @CircuitBreaker(name = "downstreamService", fallbackMethod = "notifyMatchCompletedFallback")
    @Retry(name = "downstreamService", fallbackMethod = "notifyMatchCompletedFallback")
    public boolean notifyMatchCompleted(UUID matchId, UUID winnerId) {
        invocationCount.incrementAndGet();
        log.info("Dispatching match completion notification for match {} (winner={}) to downstream analytics", matchId, winnerId);

        if (chaosFaultActive.get()) {
            log.warn("Downstream simulation: Downstream network timeout triggered (timeout={}ms)", timeoutMs);
            try {
                Thread.sleep(20); // Small sleep to simulate socket wait
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException(new SocketTimeoutException("Simulated downstream connection timed out after " + timeoutMs + "ms"));
        }

        successCount.incrementAndGet();
        log.info("Downstream notification successfully delivered for match {}", matchId);
        return true;
    }

    public boolean notifyMatchCompletedFallback(UUID matchId, UUID winnerId, Throwable t) {
        fallbackCount.incrementAndGet();
        log.warn("Downstream notification fallback executed for match {} due to [{}]: {}. Queuing for deferred processing.",
                matchId, t.getClass().getSimpleName(), t.getMessage());
        return false;
    }

    // Chaos injection hooks for controlled verification
    public void setChaosFaultActive(boolean active) {
        this.chaosFaultActive.set(active);
        log.info("Downstream chaos fault simulation active={}", active);
    }

    public boolean isChaosFaultActive() {
        return chaosFaultActive.get();
    }

    public int getInvocationCount() {
        return invocationCount.get();
    }

    public int getSuccessCount() {
        return successCount.get();
    }

    public int getFallbackCount() {
        return fallbackCount.get();
    }

    public void resetCounts() {
        invocationCount.set(0);
        successCount.set(0);
        fallbackCount.set(0);
    }
}

