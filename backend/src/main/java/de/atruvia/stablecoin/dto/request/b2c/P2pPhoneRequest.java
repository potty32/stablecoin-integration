package de.atruvia.stablecoin.dto.request.b2c;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record P2pPhoneRequest(
        @NotBlank String sourceIban,
        @NotBlank String recipientPhone,
        @NotNull @Positive BigDecimal amountEur
) {}
