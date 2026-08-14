package de.atruvia.stablecoin.dto.request.b2c;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record YieldDepositRequest(
        @NotBlank String sourceIban,
        @NotNull @Positive BigDecimal amountEur
) {}
