package de.atruvia.stablecoin.dto.request.b2b;

import de.atruvia.stablecoin.entity.StablecoinCurrency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddAddressRequest(
        @NotBlank String label,
        @NotBlank String walletAddress,
        @NotNull StablecoinCurrency currency
) {}
