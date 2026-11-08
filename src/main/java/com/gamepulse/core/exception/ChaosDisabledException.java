package com.gamepulse.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when artificial chaos fault injection or manual circuit breaker transition
 * is attempted in an environment where chaos engineering controls are disabled.
 */
public class ChaosDisabledException extends GamePulseException {

    public ChaosDisabledException(String message) {
        super(message, HttpStatus.FORBIDDEN, "CHAOS_DISABLED");
    }
}
