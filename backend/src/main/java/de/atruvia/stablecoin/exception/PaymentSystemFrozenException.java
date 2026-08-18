package de.atruvia.stablecoin.exception;

public class PaymentSystemFrozenException extends RuntimeException {
    public PaymentSystemFrozenException(String message) {
        super(message);
    }
}
