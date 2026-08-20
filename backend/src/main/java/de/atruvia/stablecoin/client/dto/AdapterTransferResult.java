package de.atruvia.stablecoin.client.dto;

public record AdapterTransferResult(
        String adapterTransactionId,
        String blockchainHash
) {}
