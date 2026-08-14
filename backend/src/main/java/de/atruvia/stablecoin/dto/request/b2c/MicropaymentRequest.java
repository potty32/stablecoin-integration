package de.atruvia.stablecoin.dto.request.b2c;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record MicropaymentRequest(
        @NotBlank String sourceIban,
        @NotBlank String destinationMerchantId,
        @NotNull @Positive BigDecimal amountEur,
        @NotBlank String contentId,
        @NotBlank String biometricToken
) {}
