package com.gamepulse.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Base domain exception for GamePulse services.
 */
public class GamePulseException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public GamePulseException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public GamePulseException(String message, Throwable cause, HttpStatus status, String errorCode) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
