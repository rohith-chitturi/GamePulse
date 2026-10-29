package com.gamepulse.core.idempotency.dto;

public class IdempotencyResult {

    private final boolean replayed;
    private final Integer responseStatus;
    private final String contentType;
    private final String responseBody;

    public IdempotencyResult(boolean replayed, Integer responseStatus, String contentType, String responseBody) {
        this.replayed = replayed;
        this.responseStatus = responseStatus;
        this.contentType = contentType;
        this.responseBody = responseBody;
    }

    public static IdempotencyResult newExecution() {
        return new IdempotencyResult(false, null, null, null);
    }

    public static IdempotencyResult cached(Integer responseStatus, String contentType, String responseBody) {
        return new IdempotencyResult(true, responseStatus, contentType, responseBody);
    }

    public boolean isReplayed() {
        return replayed;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getContentType() {
        return contentType;
    }

    public String getResponseBody() {
        return responseBody;
    }
}

