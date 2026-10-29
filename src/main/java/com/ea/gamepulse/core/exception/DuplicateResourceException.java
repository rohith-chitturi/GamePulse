package com.ea.gamepulse.core.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends GamePulseException {

    public DuplicateResourceException(String resourceName, String field, Object value) {
        super(String.format("%s with %s '%s' already exists", resourceName, field, value),
                HttpStatus.CONFLICT,
                "DUPLICATE_RESOURCE");
    }
}
