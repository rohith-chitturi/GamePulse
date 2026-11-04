package com.gamepulse.core.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("POST", "/api/v1/players");
    }

    @Test
    @DisplayName("Exception Translation: Database connection failure returns 503 SERVICE_UNAVAILABLE")
    void testDatabaseConnectionFailureTranslation() {
        CannotCreateTransactionException ex = new CannotCreateTransactionException("Could not open JDBC Connection for transaction");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDatabaseConnectionException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("DATABASE_UNAVAILABLE");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/players");
    }

    @Test
    @DisplayName("Exception Translation: CallNotPermittedException returns 503 SERVICE_UNAVAILABLE with breaker name")
    void testCallNotPermittedExceptionTranslation() {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("redisService");
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(cb);

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleCallNotPermittedException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("CIRCUIT_BREAKER_OPEN");
        assertThat(response.getBody().getMessage()).contains("redisService");
    }

    @Test
    @DisplayName("Exception Translation: RequestNotPermitted returns 429 TOO_MANY_REQUESTS")
    void testRequestNotPermittedTranslation() {
        RateLimiter rateLimiter = RateLimiter.ofDefaults("testLimiter");
        RequestNotPermitted ex = RequestNotPermitted.createRequestNotPermitted(rateLimiter);

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleRequestNotPermitted(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
    }
}
