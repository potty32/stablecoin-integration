package de.atruvia.stablecoin.exception;

public class ComplianceBlockException extends RuntimeException {
    public ComplianceBlockException(String address, String riskScore) {
        super("Address " + address + " blocked by compliance screening. Risk score: " + riskScore);
    }
}
