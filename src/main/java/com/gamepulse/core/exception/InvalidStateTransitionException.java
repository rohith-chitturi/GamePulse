package com.gamepulse.core.exception;

import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends GamePulseException {

    public InvalidStateTransitionException(String entityName, String currentState, String targetState) {
        super(String.format("Cannot transition %s from state '%s' to '%s'", entityName, currentState, targetState),
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION");
    }
}
