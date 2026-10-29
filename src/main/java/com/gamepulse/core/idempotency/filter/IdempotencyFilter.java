package com.gamepulse.core.idempotency.filter;

import com.gamepulse.core.exception.ApiErrorResponse;
import com.gamepulse.core.exception.GamePulseException;
import com.gamepulse.core.filter.CorrelationIdFilter;
import com.gamepulse.core.idempotency.dto.IdempotencyResult;
import com.gamepulse.core.idempotency.service.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Filter that intercepts mutating requests carrying an 'Idempotency-Key' header,
 * preventing duplicate operations, safely handling concurrent submissions,
 * and replaying identical responses for legitimate retries.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // Immediately after CorrelationIdFilter
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        String method = request.getMethod().toUpperCase();

        if (!StringUtils.hasText(idempotencyKey) || !MUTATING_METHODS.contains(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        byte[] requestBody = wrappedRequest.getCachedBody();
        String payloadHash = computeSha256(requestBody);
        String path = request.getRequestURI();

        IdempotencyResult checkResult;
        try {
            checkResult = idempotencyService.reserveOrGetExisting(
                    idempotencyKey,
                    path,
                    method,
                    payloadHash
            );
        } catch (GamePulseException ex) {
            writeErrorResponse(response, ex, path);
            return;
        }

        if (checkResult.isReplayed()) {
            response.setStatus(checkResult.getResponseStatus());
            response.setHeader(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
            response.setHeader(IDEMPOTENCY_REPLAYED_HEADER, "true");
            if (checkResult.getContentType() != null) {
                response.setContentType(checkResult.getContentType());
            }
            if (checkResult.getResponseBody() != null) {
                response.getOutputStream().write(checkResult.getResponseBody().getBytes(StandardCharsets.UTF_8));
            }
            return;
        }

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);

            int status = wrappedResponse.getStatus();
            byte[] responseBytes = wrappedResponse.getContentAsByteArray();
            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
            String contentType = wrappedResponse.getContentType();

            if (status >= 200 && status < 400) {
                idempotencyService.complete(idempotencyKey, status, contentType, responseBody);
            } else {
                idempotencyService.fail(idempotencyKey);
            }

            wrappedResponse.setHeader(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
            wrappedResponse.copyBodyToResponse();
        } catch (Exception ex) {
            idempotencyService.fail(idempotencyKey);
            throw ex;
        }
    }

    private void writeErrorResponse(HttpServletResponse response, GamePulseException ex, String path) throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                ex.getStatus().value(),
                ex.getStatus().getReasonPhrase(),
                ex.getErrorCode(),
                ex.getMessage(),
                path,
                correlationId != null ? correlationId : "unknown",
                null
        );

        response.setStatus(ex.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(objectMapper.writeValueAsBytes(errorResponse));
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data != null ? data : new byte[0]);
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
