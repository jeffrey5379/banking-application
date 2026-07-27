package com.bankapp.exception;

// Thrown when a request with the same Idempotency-Key is already being executed by another
// instance/thread and the result didn't show up within the wait window - see IdempotencyStore.
public class IdempotencyInProgressException extends RuntimeException {
    public IdempotencyInProgressException(String message) {
        super(message);
    }
}
