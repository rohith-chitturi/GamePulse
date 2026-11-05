package com.gamepulse.core.exception;

import com.gamepulse.core.filter.CorrelationIdFilter;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLTransientConnectionException;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception translator producing consistent, correlation-aware API responses
 * with dedicated RFC 7807 resilience and dependency failure handlers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GamePulseException.class)
    public ResponseEntity<ApiErrorResponse> handleGamePulseException(GamePulseException ex, HttpServletRequest request) {
        log.warn("Domain exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                ex.getStatus().value(),
                ex.getStatus().getReasonPhrase(),
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI(),
                getCorrelationId(),
                null
        );
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler({
            CannotCreateTransactionException.class,
            DataAccessResourceFailureException.class,
            TransactionException.class,
            SQLTransientConnectionException.class
    })
    public ResponseEntity<ApiErrorResponse> handleDatabaseConnectionException(Exception ex, HttpServletRequest request) {
        log.error("Database connection failure on URI [{}]: {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "DATABASE_UNAVAILABLE",
                "Authoritative database is currently unreachable or connection pool exhausted. Please retry shortly.",
                request.getRequestURI(),
                getCorrelationId(),
                null
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiErrorResponse> handleCallNotPermittedException(CallNotPermittedException ex, HttpServletRequest request) {
        log.warn("Resilience4j Circuit Breaker OPEN on URI [{}]: {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "CIRCUIT_BREAKER_OPEN",
                String.format("Service circuit breaker [%s] is OPEN. Operation is temporarily unavailable.", ex.getCausingCircuitBreakerName()),
                request.getRequestURI(),
                getCorrelationId(),
                null
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiErrorResponse> handleRequestNotPermitted(RequestNotPermitted ex, HttpServletRequest request) {
        log.warn("Rate limit exceeded on URI [{}]: {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "RATE_LIMIT_EXCEEDED",
                "Request rate limit exceeded. Please throttle outgoing requests.",
                request.getRequestURI(),
                getCorrelationId(),
                null
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("Validation failure on URI [{}]: {}", request.getRequestURI(), errors);

        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "VALIDATION_FAILED",
                "Input validation failed for request payload",
                request.getRequestURI(),
                getCorrelationId(),
                errors
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalStateException(IllegalStateException ex, HttpServletRequest request) {
        log.warn("Illegal state conflict on URI [{}]: {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "INVALID_STATE",
                ex.getMessage(),
                request.getRequestURI(),
                getCorrelationId(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiErrorResponse> handleOptimisticLockingFailure(Exception ex, HttpServletRequest request) {
        log.warn("Optimistic locking conflict on URI [{}]: {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "CONCURRENCY_CONFLICT",
                "The requested resource was concurrently modified by another transaction. Please retry.",
                request.getRequestURI(),
                getCorrelationId(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument on URI [{}]: {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "ILLEGAL_ARGUMENT",
                ex.getMessage(),
                request.getRequestURI(),
                getCorrelationId(),
                null
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnhandledException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled internal server error on URI [{}]:", request.getRequestURI(), ex);
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "INTERNAL_SERVER_ERROR",
                "An unexpected internal error occurred. Please contact support with correlation ID.",
                request.getRequestURI(),
                getCorrelationId(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String getCorrelationId() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return correlationId != null ? correlationId : "unknown";
    }
}
