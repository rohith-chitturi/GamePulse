package com.gamepulse.core.idempotency.model;

/**
 * State of an idempotency execution record.
 */
public enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

