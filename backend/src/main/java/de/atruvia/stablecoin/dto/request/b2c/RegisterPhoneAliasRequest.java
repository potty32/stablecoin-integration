package de.atruvia.stablecoin.dto.request.b2c;

import jakarta.validation.constraints.NotBlank;

public record RegisterPhoneAliasRequest(
        @NotBlank String phoneNumber,
        @NotBlank String sourceIban,
        @NotBlank String walletAddress
) {}
