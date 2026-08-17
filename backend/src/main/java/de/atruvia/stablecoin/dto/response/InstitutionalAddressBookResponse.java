package de.atruvia.stablecoin.dto.response;

import de.atruvia.stablecoin.entity.InstitutionalAddressStatus;
import de.atruvia.stablecoin.entity.RiskScore;
import de.atruvia.stablecoin.entity.StablecoinCurrency;

import java.time.LocalDateTime;
import java.util.UUID;

public record InstitutionalAddressBookResponse(
        UUID id,
        String label,
        String walletAddress,
        StablecoinCurrency currency,
        RiskScore riskScore,
        InstitutionalAddressStatus status,
        String createdBy,
        LocalDateTime verifiedAt
) {}
