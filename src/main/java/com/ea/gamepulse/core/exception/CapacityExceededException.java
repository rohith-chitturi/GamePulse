package com.ea.gamepulse.core.exception;

import org.springframework.http.HttpStatus;

public class CapacityExceededException extends GamePulseException {

    public CapacityExceededException(String resourceName, int currentCapacity, int maxCapacity) {
        super(String.format("%s capacity exceeded: current=%d, max=%d", resourceName, currentCapacity, maxCapacity),
                HttpStatus.CONFLICT,
                "CAPACITY_EXCEEDED");
    }
}
