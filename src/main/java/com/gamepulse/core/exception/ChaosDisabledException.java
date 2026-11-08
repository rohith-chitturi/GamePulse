package com.gamepulse.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when chaos testing or manual circuit breaker transitions
 * are attempted while gamepulse.chaos.enabled is false.
 */
public class ChaosDisabledException extends GamePulseException {

    public ChaosDisabledException(String message) {
        super(message, HttpStatus.FORBIDDEN, "CHAOS_DISABLED");
    }
}
