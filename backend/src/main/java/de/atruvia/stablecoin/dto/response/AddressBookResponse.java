package de.atruvia.stablecoin.dto.response;

import de.atruvia.stablecoin.entity.AddressStatus;
import de.atruvia.stablecoin.entity.RiskScore;
import de.atruvia.stablecoin.entity.StablecoinCurrency;

import java.time.LocalDateTime;
import java.util.UUID;

public record AddressBookResponse(
        UUID id,
        String label,
        String walletAddress,
        StablecoinCurrency currency,
        RiskScore riskScore,
        AddressStatus status,
        LocalDateTime verifiedAt
) {}
