package com.gamepulse.core.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends GamePulseException {

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(String.format("%s with identifier '%s' not found", resourceName, identifier),
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND");
    }
}
