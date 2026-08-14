package de.atruvia.stablecoin.client.dto;

import java.util.List;

public record AddressScreenResponseDto(
        String address,
        String riskScore,
        List<String> riskCategories,
        boolean sanctionedEntity,
        boolean approved
) {}
