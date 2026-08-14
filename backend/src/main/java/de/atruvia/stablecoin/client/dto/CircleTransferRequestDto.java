package de.atruvia.stablecoin.client.dto;

public record CircleTransferRequestDto(
        String idempotencyKey,
        Source source,
        Destination destination,
        Amount amount
) {
    public record Source(String type, String id) {}
    public record Destination(String type, String address, String chain) {}
    public record Amount(String amount, String currency) {}
}
