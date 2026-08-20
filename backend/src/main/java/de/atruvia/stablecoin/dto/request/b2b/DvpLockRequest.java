package de.atruvia.stablecoin.dto.request.b2b;

import de.atruvia.stablecoin.entity.StablecoinCurrency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DvpLockRequest(
        @NotBlank  String sourceIban,
        @NotNull @Positive BigDecimal amountEur,
        @NotNull   StablecoinCurrency currency,
        @NotBlank  String settlementWallet,
        @NotBlank  String securitiesIsin,
        @NotNull @Positive BigDecimal securitiesAmount,
        @NotBlank  String escrowReference,
                   String securitiesSystemId
) {}
