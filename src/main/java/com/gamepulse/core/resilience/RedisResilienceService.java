package com.gamepulse.core.resilience;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Resilient distributed cache adapter protected by Resilience4j Circuit Breaker.
 * Seamlessly degrades to empty responses when Redis is offline or failing,
 * preventing downstream latency spikes and service outages.
 */
@Service
public class RedisResilienceService {

    private static final Logger log = LoggerFactory.getLogger(RedisResilienceService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gamepulse.resilience.redis.enabled:true}")
    private boolean redisEnabled;

    @Value("${gamepulse.resilience.redis.ttl-seconds:300}")
    private long defaultTtlSeconds;

    @Autowired
    public RedisResilienceService(Optional<StringRedisTemplate> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate.orElse(null);
        this.objectMapper = objectMapper;
    }

    public boolean isRedisAvailable() {
        return redisEnabled && redisTemplate != null;
    }

    @CircuitBreaker(name = "redisService", fallbackMethod = "getFallback")
    public <T> Optional<T> get(String key, Class<T> targetClass) {
        if (!isRedisAvailable()) {
            return Optional.empty();
        }
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(value, targetClass));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize Redis value for key {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public <T> Optional<T> getFallback(String key, Class<T> targetClass, Throwable t) {
        log.warn("Redis get fallback activated for key '{}' due to [{}]: {}. Degrading gracefully to database query.",
                key, t.getClass().getSimpleName(), t.getMessage());
        return Optional.empty();
    }

    @CircuitBreaker(name = "redisService", fallbackMethod = "setFallback")
    public void set(String key, Object value, Duration ttl) {
        if (!isRedisAvailable()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            Duration effectiveTtl = ttl != null ? ttl : Duration.ofSeconds(defaultTtlSeconds);
            redisTemplate.opsForValue().set(key, json, effectiveTtl);
            log.debug("Cached key '{}' in Redis with TTL={}", key, effectiveTtl);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object for Redis key {}: {}", key, e.getMessage());
        }
    }

    public void setFallback(String key, Object value, Duration ttl, Throwable t) {
        log.warn("Redis set fallback activated for key '{}' due to [{}]: {}. Skipping cache update.",
                key, t.getClass().getSimpleName(), t.getMessage());
    }

    @CircuitBreaker(name = "redisService", fallbackMethod = "deleteFallback")
    public void delete(String key) {
        if (!isRedisAvailable()) {
            return;
        }
        redisTemplate.delete(key);
        log.debug("Evicted key '{}' from Redis", key);
    }

    public void deleteFallback(String key, Throwable t) {
        log.warn("Redis delete fallback activated for key '{}' due to [{}]: {}. Cache entry will expire naturally.",
                key, t.getClass().getSimpleName(), t.getMessage());
    }
}

