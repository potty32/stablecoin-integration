package de.atruvia.stablecoin.dto.request.b2b;

import de.atruvia.stablecoin.entity.StablecoinCurrency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record InitiateTransferRequest(
        @NotBlank String sourceIban,
        @NotBlank String destinationWallet,
        @NotNull @Positive BigDecimal amountEur,
        @NotNull StablecoinCurrency currency,
        UUID rateQuoteId,
        String purposeCode,
        String reference,
        // G-12: Travel Rule (FATF Rec. 16) — Pflichtfelder bei amountEur > TravelRuleThreshold
        String beneficiaryName,
        String beneficiaryAddress,
        String beneficiaryAccountId
) {}
