package de.atruvia.stablecoin.exception;

import java.util.UUID;

public class IdempotencyConflictException extends RuntimeException {
    private final UUID existingTransactionId;

    public IdempotencyConflictException(UUID existingTransactionId) {
        super("Transaction with this idempotency key already exists: " + existingTransactionId);
        this.existingTransactionId = existingTransactionId;
    }

    public UUID getExistingTransactionId() {
        return existingTransactionId;
    }
}
