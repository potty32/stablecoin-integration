package de.atruvia.stablecoin.dto.response;

public record BulkRowResult(
        int rowNumber,
        String destinationWallet,
        String amountEur,
        String status,
        String message,
        String transactionId
) {}
