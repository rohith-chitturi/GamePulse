package com.gamepulse.core.exception;

import org.springframework.http.HttpStatus;

public class ConcurrentIdempotentRequestException extends GamePulseException {

    public ConcurrentIdempotentRequestException(String key) {
        super(String.format("An in-flight request with idempotency key '%s' is currently being processed", key),
                HttpStatus.CONFLICT,
                "CONCURRENT_REQUEST_IN_FLIGHT");
    }
}

