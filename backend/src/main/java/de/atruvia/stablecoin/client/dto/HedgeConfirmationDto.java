package de.atruvia.stablecoin.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HedgeConfirmationDto(
        String hedgeId,
        BigDecimal confirmedRate,
        BigDecimal hedgeAmount,
        LocalDateTime confirmedAt,
        String status   // "OPEN" | "SETTLED" | "CLOSED"
) {}
