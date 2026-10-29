package com.gamepulse.core.exception;

import org.springframework.http.HttpStatus;

public class ConflictingIdempotencyKeyException extends GamePulseException {

    public ConflictingIdempotencyKeyException(String key) {
        super(String.format("Idempotency key '%s' has already been used with a different request payload or endpoint", key),
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_PAYLOAD_MISMATCH");
    }
}

