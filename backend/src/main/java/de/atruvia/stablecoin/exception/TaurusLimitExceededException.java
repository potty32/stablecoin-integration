package de.atruvia.stablecoin.exception;

public class TaurusLimitExceededException extends RuntimeException {
    public TaurusLimitExceededException(String message) {
        super(message);
    }
}
