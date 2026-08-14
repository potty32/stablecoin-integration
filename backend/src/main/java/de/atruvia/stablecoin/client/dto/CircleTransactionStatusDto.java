package de.atruvia.stablecoin.client.dto;

public record CircleTransactionStatusDto(
        String id,
        String status,
        String transactionHash,
        NetworkFee networkFee
) {
    public record NetworkFee(String amount, String currency) {}
}
