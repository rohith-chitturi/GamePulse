package com.gamepulse.core.idempotency.service;

import com.gamepulse.core.exception.ConcurrentIdempotentRequestException;
import com.gamepulse.core.exception.ConflictingIdempotencyKeyException;
import com.gamepulse.core.idempotency.dto.IdempotencyResult;
import com.gamepulse.core.idempotency.model.IdempotencyRecord;
import com.gamepulse.core.idempotency.model.IdempotencyStatus;
import com.gamepulse.core.idempotency.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository repository;

    @Value("${gamepulse.idempotency.ttl-hours:24}")
    private int ttlHours;

    @Value("${gamepulse.idempotency.in-flight-timeout-seconds:120}")
    private int inFlightTimeoutSeconds;

    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyResult reserveOrGetExisting(String key, String path, String method, String payloadHash) {
        Optional<IdempotencyRecord> existingOpt = repository.findByIdempotencyKey(key);

        if (existingOpt.isPresent()) {
            return handleExistingRecord(existingOpt.get(), key, path, method, payloadHash);
        }

        try {
            Instant expiresAt = Instant.now().plus(Duration.ofHours(ttlHours));
            IdempotencyRecord newRecord = new IdempotencyRecord(key, path, method, payloadHash, expiresAt);
            repository.saveAndFlush(newRecord);
            log.debug("Reserved idempotency key: key={}, path={}", key, path);
            return IdempotencyResult.newExecution();
        } catch (DataIntegrityViolationException ex) {
            // Concurrent race condition: another thread inserted the key simultaneously
            log.debug("Concurrent reservation conflict for key: {}, recovering", key);
            IdempotencyRecord winner = repository.findByIdempotencyKey(key)
                    .orElseThrow(() -> new ConcurrentIdempotentRequestException(key));
            return handleExistingRecord(winner, key, path, method, payloadHash);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String key, int responseStatus, String contentType, String responseBody) {
        repository.findByIdempotencyKey(key).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setResponseStatus(responseStatus);
            record.setResponseContentType(contentType);
            record.setResponseBody(responseBody);
            repository.saveAndFlush(record);
            log.debug("Idempotent operation completed: key={}, status={}", key, responseStatus);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String key) {
        repository.findByIdempotencyKey(key).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.FAILED);
            repository.saveAndFlush(record);
            log.debug("Idempotent operation marked failed: key={}", key);
        });
    }

    private IdempotencyResult handleExistingRecord(IdempotencyRecord record, String key, String path, String method, String payloadHash) {
        // 1. Verify path and payload hash match exactly
        if (!record.getRequestPath().equals(path) || !record.getRequestPayloadHash().equals(payloadHash)) {
            throw new ConflictingIdempotencyKeyException(key);
        }

        // 2. Check current status
        if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
            // Check if record is stale due to an unhandled crash
            Duration elapsed = Duration.between(record.getCreatedAt(), Instant.now());
            if (elapsed.getSeconds() > inFlightTimeoutSeconds) {
                log.warn("Reclaiming stale in-progress idempotency key after crash: key={}, elapsedSeconds={}", key, elapsed.getSeconds());
                record.setStatus(IdempotencyStatus.IN_PROGRESS);
                record.setCreatedAt(Instant.now());
                repository.saveAndFlush(record);
                return IdempotencyResult.newExecution();
            }
            throw new ConcurrentIdempotentRequestException(key);
        }

        if (record.getStatus() == IdempotencyStatus.COMPLETED) {
            log.info("Replaying cached response for idempotency key: key={}, path={}", key, path);
            return IdempotencyResult.cached(
                    record.getResponseStatus(),
                    record.getResponseContentType(),
                    record.getResponseBody()
            );
        }

        // If previously failed, allow retry
        record.setStatus(IdempotencyStatus.IN_PROGRESS);
        repository.saveAndFlush(record);
        return IdempotencyResult.newExecution();
    }
}

