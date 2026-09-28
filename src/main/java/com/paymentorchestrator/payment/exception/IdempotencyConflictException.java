package com.paymentorchestrator.payment.exception;

/**
 * Thrown when a request reuses an idempotency key that is currently being
 * processed by another in-flight request, and no persisted payment exists
 * for it yet. This is a genuine "come back in a moment" situation, distinct
 * from a normal idempotent replay (where we already have a result to return).
 * Maps to HTTP 409.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String idempotencyKey) {
        super("A request with idempotency key '" + idempotencyKey + "' is already being processed");
    }
}
