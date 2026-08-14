package de.atruvia.stablecoin.client.dto;

import java.math.BigDecimal;

public record CreateHoldDto(BigDecimal amount, String currency, String reason, String referenceId) {}
