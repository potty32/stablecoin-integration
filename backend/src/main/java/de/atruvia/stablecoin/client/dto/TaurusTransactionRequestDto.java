package de.atruvia.stablecoin.client.dto;

public record TaurusTransactionRequestDto(
        String assetId,
        String fromAddress,
        String toAddress,
        String amount,
        Metadata metadata
) {
    public record Metadata(String referenceId, String customerId) {}
}
