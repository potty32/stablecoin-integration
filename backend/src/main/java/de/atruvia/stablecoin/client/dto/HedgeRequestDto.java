package de.atruvia.stablecoin.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HedgeRequestDto(
        BigDecimal eurAmount,
        String currency,       // "USDC" oder "EURC"
        BigDecimal quotedRate,
        LocalDateTime expiresAt,
        String txReference     // Plattform-interne Referenz (RateQuote.id)
) {}
